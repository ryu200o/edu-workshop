# ADR 0015: Concurrency Control & Race Condition Mitigation Strategy

* **Status**: ACCEPTED
* **Date**: 2026-08-03
* **Deciders**: Lead Engineer, Solution Architect (SA)
* **Technical Domain**: Transactional Integrity, Locking Strategy (`Pessimistic` / `Optimistic`), Application Orchestration (ADR 0005), Database Constraints, Shared Kernel

---

## 1. Context & Problem Statement

During the technical review of the **Time-Windowed Room Maintenance** feature, the team identified a latent **Race Condition (Phantom Read / Write Skew)** risk: two (or more) concurrent requests within the same millisecond could schedule **overlapping maintenance windows** for the same room.

Because transactions run at the default **`READ COMMITTED`** isolation level, the naive pattern of "plain `loadById` + `findOverlapping` in the Application Handler" cannot prevent two threads from:

1. Reading the same stale, non-overlapping state (Phantom Read),
2. Both passing the overlap check,
3. Both persisting schedules that overlap each other (Write Skew).

Without an explicit concurrency strategy, every set-based / temporal-overlap invariant (scheduling, rescheduling, registration) is exposed to the same class of failure. The team therefore needs a standardized **Concurrency Control Matrix** so the correct locking mechanism is applied consistently and deliberately, not ad-hoc per feature.

### Why this needs to be documented

* Different concurrent risks require different remedies. Applying the wrong lock (e.g. optimistic on a heavy-overlap feature, or pessimistic on low-conflict single-aggregate mutation) wastes DB connections or leaves race windows open.
* New features (`ScheduleRoomMaintenanceCommand`, `RescheduleWorkshopCommand`, `RegisterWorkshopCommand`) must choose a strategy by pattern, not by taste.
* The mitigation must respect the existing architecture: **AGGREGATE methods stay IO-free and policy-free** (ADR 0005). Locking is a *persistence/transaction* concern that lives in the **Application Handler + Outbound Port**, never in the aggregate.

---

## 2. Decision Drivers

* **Consistency**: No silent data corruption under concurrent writes (overlapping schedules, duplicated codes, oversubscription).
* **Connection-Pool Efficiency**: Heavy pessimistic locking on hot, low-conflict write paths wastes database connection slots.
* **Clean Business Errors**: Concurrency violations must surface as **precise, intentional business errors** (`409 CONFLICT`), not opaque `DataIntegrityViolation` / `500`.
* **Boundary Integrity**: No policy/repository/lock API ever leaks into the domain aggregate (ADR 0005).
* **Determinism**: Developers choose a technique on the basis of *what the handler validates*, not intuition.

---

## 3. Decision Outline

### 3.1 The Concurrency Control Matrix

EduWorkshop standardizes **four** concurrency-control techniques. A Command Handler MUST select the technique the invariant actually requires:

| # | Technique | Mandatory when ... | Typical commands | Failure surface |
|---|-----------|--------------------|------------------|-----------------|
| 1 | **Pessimistic Write Lock** (`SELECT ... FOR UPDATE` / `loadByIdWithLock`) | The handler must validate a **Set-Based Invariant** or a **Temporal-Overlap Check** whose correctness depends on querying more than one persisted record *inside the same transaction*. | `ScheduleRoomMaintenanceCommand`, `RescheduleWorkshopCommand`, `RegisterWorkshopCommand` | Serializes concurrent transactions at the DB; raises clean `409 CONFLICT` |
| 2 | **Optimistic Locking** (`@Version`) | The operation mutates the state of **exactly one Aggregate Root** (Single-Aggregate Mutation) and the concurrent-conflict probability is low. | `RenameRoomCommand`, `UpdateUserProfileCommand` | Fast-fail on `OptimisticLockException`; retry at Application layer |
| 3 | **Database Unique Constraint (DB Backstop)** | The rule is a **uniqueness/identity check** on a scalar value or composite pair (Room code, User email, unique index on `(workshop_id, user_id)`). | Room create/rename, Registration (`uk_registrations_workshop_user`) | Final race-proof gate; Persistence adapter translates `DataIntegrityViolationException` |
| 4 | **Atomic DB Operation** | The update is a **simple numeric increment/decrement** with a guard. | `UPDATE inventory SET stock = stock - 1 WHERE id = ? AND stock > 0` | No read-modify-write; single statement is atomic by construction |

> **Rule of thumb**: If the handler validates **cross-record** business rules (overlap, occupancy, existence-of-set), you need **Technique 1** (pessimistic) so the arbiter sees a *consistent snapshot of the whole set*. If it validates only **one aggregate's** internal state, **Technique 2** (optimistic) is sufficient. **Technique 3** and **Technique 4** handle the remaining identity/arithmetic cases.

### 3.2 Pessimistic Write Lock — details

- The outbound **write port** (`RoomRepository` and peers) exposes a locked loader:
  ```java
  Optional<Room> loadByIdWithLock(RoomId id);   // SELECT ... FOR UPDATE
  ```
- The JPA adapter implements it with `@Lock(LockModeType.PESSIMISTIC_WRITE)` (or an equivalent row lock). When two transactions target the same root, the second **blocks** until the first commits/rolls back, then re-reads fresh committed data — eliminating phantom-read/write-skew on the checked set.
- Handler shapes the operation as: `withLock(load) → re-validate overlap on locked data → aggregate local guard → persist → publish`.
- The lock guarantees the **overlap check and the insert are effectively serialized** per aggregate root (the room), which is the smallest safe serialization scope.
- PostgreSQL semantics: `SELECT ... FOR UPDATE` locks rows returned; two overlapping maintenance schedules for the *same* room serialize on the shared `room_id` row. Locking the `room` row (the aggregate root) is the canonical approach; the schedule table itself is INSERT-only and need not be the lock target.

### 3.3 Optimistic Locking — details

- The JPA entity carries a `@Version` long.
- On conflict, the persistence layer throws JPA's `OptimisticLockException` / Spring's `ObjectOptimisticLockingFailureException`; the Application layer translates to a domain business error and MAY provide an idempotent retry.
- Applicable **only** when the mutation is a single-aggregate self-contained change and conflict probability is low; avoids holding DB connections open.

### 3.4 DB Unique Constraint as final backstop — details

- The **authoritative race-proof gate** remains the row-level unique constraint/unique index (ADR 0005 §5). The Application's `findBy*` read is a fast-fail / UX optimization only.
- The persistence adapter (e.g. `JpaRoomWriteAdapter.save`) translates `DataIntegrityViolationException` into the domain-specific duplicate exception (e.g. `DuplicateRoomCodeException`, `DuplicateRoomNameException`) that maps to `409 CONFLICT`.
- Unique index `uk_registrations_workshop_user` (ADR 0012) is a portable race-proof backstop even when H2/Postgres lack partial indexes.

### 3.5 Where each technique lives

- `lock` behavior is a **persistence/adapter** responsibility and a **write port** contract. It is **NOT** a domain concern.
- Application handles orchestrate: choose the port method (`loadByIdWithLock` for Technique 1), run the overlap/set query, call the aggregate, save.
- **Aggregate signatures never change** — they receive only domain parameters. Locking stays invisible to the domain.

---

## 4. Consequences

### Positive

- **Two concurrent maintenance requests can no longer double-book a room.** The shared `room_id` row lock serializes the overlap check.
- **Predictable error surface**: overlap/occupancy violations surface as intentional, well-named exceptions mapped to `409 CONFLICT`, instead of generic constraint violations.
- **Cheap-by-default**: only handlers that genuinely validate cross-record invariants pay the pessimistic-lock cost (one row, serialized), while single-aggregate mutations stay optimistic.
- **Boundary intact**: aggregate stays IO-free; the matrix is implemented entirely in ports + adapters + Application.

### Trade-offs / Costs

- **Held locks**: pessimistic write locks are held until transaction commit; the write transaction must be **short** (no external IO, no cross-message awaits) to avoid pool exhaustion.
- **Deadlock care**: always lock aggregates in a **consistent order** across handlers to avoid deadlocks; the matrix encourages locking the *root row* first.
- **No blanket** pessimistic coating: adding locks to every handler would pessimize throughput on high-frequency, low-conflict writes — hence the matrix.

---

## 5. Standard Exception Mapping

The following mapping is prescribed for the Room module (and reused by every module):

| Exception | HTTP Status | Example |
|---|---|---|
| `RoomNotFoundException` | `404 NOT_FOUND` | missing room id |
| `DuplicateRoomCodeException` / `DuplicateRoomNameException` | `409 CONFLICT` | uniqueness backstop fired |
| `IllegalRoomStateException` | `409 CONFLICT` | transition not allowed by state machine |
| **`MaintenanceScheduleOverlapException`** | **`409 CONFLICT`** | overlapping maintenance window detected |
| `InvalidMaintenanceScheduleException` (local VO invariant) | `400 BAD_REQUEST` | reason too short / invalid time window |
| `RoomDomainException` (other domain invariant) | `400 BAD_REQUEST` | generic local invariant |
| `RoomPersistenceException` | `500 INTERNAL_SERVER_ERROR` | unexpected DB failure |

> A **global/set-based violation** (e.g. overlap) is a *conflict with existing state* → **409**. A **local value-invariant violation** (e.g. malformed time window) is *client error* → **400**. This split follows ADR 0005's exception layering (`application/exception` = global, `domain/.../exception` = local).

---

## 6. Implementation Checklist

1. **ADR docs**: this document (ADR 0015) created; ADR 0005 already covers orchestration and is cross-referenced.
2. **Port**: `RoomRepository.loadByIdWithLock(RoomId)` added.
3. **Adapter**: `RoomJpaRepository` exposes `@Lock(PESSIMISTIC_WRITE) findByIdForUpdate`; `JpaRoomWriteAdapter` maps it to the domain.
4. **Domain**: `MaintenanceSchedule.create` rejects an `endTime` in the past (`!endTime.isAfter(now)`); constant locality preserved.
5. **Application**: `ScheduleRoomMaintenanceCommandHandler` uses `loadByIdWithLock`, throws **`MaintenanceScheduleOverlapException`** (extends `ApplicationException`) on overlap; imports exceptions directly (no FQN in body).
6. **HTTP**: `RoomExceptionAdvice` maps `MaintenanceScheduleOverlapException` → `409 CONFLICT`.
7. **Tests**: handler, adapter, domain, and HTTP-advice E2E updated and running green.

---

## 7. Glossary

- **Phantom Read**: reading a different set of rows across two queries in one transaction because of a concurrent insert.
- **Write Skew**: two transactions each read an overlapping-but-non-violating snapshot, both commit, and together violate a constraint.
- **Pessimistic Lock**: DB-level row lock that forces concurrent writers to queue (serialize).
- **Optimistic Lock**: a version/`@Version` check that fails the transaction on stale writes, trading a failed write for no held lock.

---

*Supersedes/revetes no prior ADR; complements ADR 0002 (orchestration), ADR 0005 (global orchestration), ADR 0011 (outbox/event publication), ADR 0012 (unique registration index).*