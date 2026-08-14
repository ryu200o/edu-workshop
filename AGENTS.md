# AGENTS.md — Agent System Instructions

This file defines the behavioral rules and architecture conventions that AI agents (and contributors)
MUST follow when working in this repository. Read it before making changes.

## Project Architecture

- **Top-level style:** Modular Monolith powered by **Spring Modulith**, which manages the module
  boundaries and enforces the *allowed dependencies* between modules (the outer ring).
- **Inner style per module:** **Hexagonal Architecture** (Ports & Adapters) combined with
  **DDD Tactical** patterns inside the `internal/` area.

### Conceptual layering

- `contract/` — public contracts shared *across* modules (DTOs, integration events). These are
  part of the module's public API and must NOT reside under `internal/` (per ADR 0010).
- `internal/` — the encapsulated core of a module (domain, application, adapters, facade). Information
  hiding boundary: by default everything here is **package-private**.
- Cross-module request handling lives in `internal/facade/` (the Module Facade, per ADR 0010).

## Branch Strategy

- NO large/long-lived feature branches. Apply **Short-lived branches**.
- Slice features finely following the order: **Domain -> DB Adapter -> Expose API**.
- **Each slice = one commit** on the same feature branch (no small per-slice PRs). Every commit must leave the build/tests green before the next one.
- **Open a PR only when the feature task that the branch represents is complete** (all slices done) → merge that single PR into `main`.

## Package Rules

- The `internal/` zone is an information-hiding boundary. Default visibility is **package-private**;
  only explicitly intended types are `public`.
- The Module Facade layer MUST be named **`facade`** (underscore, not hyphen) and lives at
  `internal/facade/` — conceptually distinct from inbound adapters (per ADR 0010).
- Module API communication interface is exposed as `[ModuleName]ExposeAPI` (public, at module root);
  its implementation `[ModuleName]ExposeAPIImpl` lives in `internal/facade/` and stays package-private.
- The Facade may coordinate directly with Application Ports (Reader, Repository, Domain Service)
  **without going through the Command/Query Bus** — this is a trusted cross-module collaboration,
  not an external entry point (per ADR 0010).
- **Exception layering (3 categories):**
  - **Local invariant violations** (raised by the aggregate, proven by a single object's state):
    `IllegalRoomStateException`, `RoomDomainException` — stay in `internal/domain/model/exception/`.
  - **Global / set-based rule violations** (raised by Application handlers per ADR 0005, proven by
    cross-aggregate query): `DuplicateRoomCodeException`, `DuplicateRoomNameException` — live in
    `internal/application/exception/` and extend `shared.application.exception.ApplicationException`.
  - **Failed lookup / not-found** (raised by handlers after empty port lookup): `RoomNotFoundException`
    — lives in `internal/application/exception/` and extends `ResourceNotFoundException`.
  The domain never imports application exceptions. New modules follow the same split.

## Outbound Port Naming & DI Convention

- Read/write outbound ports are symmetric: **`RoomRepository`** (write: `loadById` / `save` only) and
  **`RoomReader`** (read, CQRS bypass). (Formerly `RoomQueryPort` — renamed; the CQS `Query`/`QueryBus`/
  `QueryHandler` message concepts are NOT affected.)
- DI field names are **non-abbreviated and type-derived** — always `RoomRepository roomRepository` and
  `RoomReader roomReader`. Never abbreviate to `repository` / `reader`.
- Implementations: `JpaRoomWriteAdapter` (impl `RoomRepository`, persistence/jpa) and `JooqRoomReadAdapter`
  (impl `RoomReader`, persistence/jooq) — adapter class names are intentional and kept as-is.
- **Uniqueness is an Application orchestration concern** (per ADR 0005 revised). The handler queries
  the repository and evaluates uniqueness before calling the aggregate. No policy/interface is injected
  into the aggregate.

## Modules & Skeleton

- Generate new modules with `bash create-module.sh <module-name>`. It produces the Spring Modulith +
  Hexagonal skeleton and the required `ExposeAPI` / `ExposeAPIImpl` pair.
- Application layer (per module) follows the golden structure: `application/port/inbound/{command,query}`,
  `application/port/outbound`, flat `application/handler`, `application/event`, `application/mapper`.

## Documentation Index (project "constitution")

Consult these before designing or coding. They are the source of truth:

- `docs/architecture/development-guidelines.md` — golden Application structure + Command/Query bus reference code.
- `docs/architecture/adr/0001-isolate-room-static-and-temporal-states.md` — Room static vs temporal state isolation.
- `docs/architecture/adr/0002-application-layer-restructuring-and-cqs-bypass.md` — Application layout,
  reject `domain/spi`, package-private handlers, CQRS bypass, per-module Command/Query bus.
  **Revised to add**: aggregate local invariants only, Application orchestrates global rules,
  Repository only in Application, Domain Service as pure concept, Event as integration.
- `docs/architecture/adr/0005-global-business-rules-application-orchestration.md` — **Revised (supersedes
  old policy injection)**: global business rules (uniqueness, overlap, existence) belong to Application
  orchestration, NOT injected into the aggregate. Aggregate enforces only local invariants. No Repository
  or Policy interface is passed into domain methods. Both Workshop and Room modules comply.
- `docs/architecture/adr/0006-shared-command-query-bus.md` — Shared Command/Query Bus (supersedes ADR 0002 §5):
  shared kernel owns the bus; modules own Commands/Queries/Handlers.
- `docs/architecture/adr/0007-cross-module-data-decoupling-via-selective-snapshotting.md` — **Proposed**:
  Workshop decouples from Room via logical `room_id` UUID + selective `room_name_snapshot` /
  `room_location_snapshot` columns (no physical FK / cross-module JOIN); proactive sync via `RoomExposeAPI`,
  reactive sync deferred until Room events are published.
- `docs/architecture/adr/0008-room-allocation-policy-planning-vs-reservation.md` — **Proposed**: Room
  allocation is *planning* at `PLANNED` (no exclusive reservation; overlapping schedules allowed) and
  *reservation* only at `PUBLISHED` (publish-time conflict check in Application layer). Room Availability
  has 3 states (AVAILABLE / AVAILABLE_WITH_PLANNING_CONFLICT / OCCUPIED); aggregate stays pure.
- `docs/architecture/adr/0009-vo-purity-conditional-field-objectification.md` — **Proposed**: VO Purity
  standard. Conditional fields → self-validating VO; unconditional fields → keep primitive; domain only
  null-checks VOs (no business-rule re-checks); Application only builds VOs + calls domain. Global/set-based
  invariants stay in a Policy (ADR 0005) — the sole exception. Room `capacity`/`code` primitives to be
  objectized; Workshop domain is the reference implementation.
- `docs/architecture/adr/0010-cross-module-dependencies-are-application-only.md` — **Accepted**:
  Cross-module dependencies are Application-only. Contract types stay outside `internal/`;
  Module Facade (`internal/facade/`) is distinct from inbound adapters. Domain must never import
  another module's API or contract DTOs. The Application layer acts as Anti-Corruption Layer (ACL).
- `docs/architecture/adr/0011-event-publication-registry-as-outbox.md` — **Accepted**:
  Spring Modulith Event Publication Registry is the transactional outbox (below ports & adapters,
  transparent to Application). One `event_publication` row per (event, listener) in the business
  TX; completion via UPDATE `completion_date`; restart replay opt-in.
- `docs/architecture/adr/0012-registration-table-one-row-per-workshop-user-with-status-flip.md` —
  **Accepted**: `registrations` keeps EXACTLY ONE row per (workshop_id, user_id); cancel flips
  `status` to `CANCELLED`, re-register flips it back (plain unique index
  `uk_registrations_workshop_user` = race-proof backstop, portable across H2/PostgreSQL —
  H2 lacks partial indexes). `workshop_start_time` selective snapshot (ADR 0007). Phase 1
  implements a subset (REGISTERED/CANCELLED, no check-in); `docs/db/database.md` §6 stays the
  target design and is synchronized only when the module is complete. No `registration_histories`.
- `docs/architecture/adr/0014-registration-12h-grace-period.md` — **Accepted**: Registration grants
  active seats a 12-hour urgent-cancellation grace window on workshop reschedule
  (`Registration.grantGracePeriod(occurredAt, newStartTime, now)`, system-initiated refund
  `refundBySystem(now)` bypasses the 24h deadline). `workshop_start_time` snapshot refreshed in the
  same flip. Consumed via `WorkshopRescheduledIntegrationEvent`/`WorkshopCancelledIntegrationEvent`
  (outbox, ADR 0011). Phase 1 subset only.
- `docs/architecture/adr/0015-concurrency-control-and-race-condition-mitigation.md` — **Accepted**:
  Standardized concurrency matrix. **Set-based (write-skew) invariants**: lock-set-first with
  pessimistic `@Lock(PESSIMISTIC_WRITE)` aggregate loads (e.g.
  `WorkshopRepository.loadPublishedAndPlannedOverlappingWithLock`,
  `MaintenanceScheduleRepository.loadOverlapping`, room `loadByIdWithLock`). **Single-aggregate
  mutations**: optimistic locking — nullable `@Version Long` columns (`V14`), `ObjectOptimisticLockingFailureException` →
  **409**, no `isNew()` reliance on assigned-UUID entities. Write adapters follow the 3 Golden
  Save/Flush rules: Room & Registration `saveAndFlush` + `DataIntegrityViolation` backstop;
  Workshop & Registration.saveAll use plain `save()` (dirty-check).
- `docs/architecture/adr/0016-port-and-exposeapi-method-naming-convention.md` — **Accepted**:
  Outbound ports & Module Facades must not mix Write/Read prefixes. Write ports (`*Repository`)
  use **`load*`** (e.g. `loadById`, `loadPublishedOverlappingWithLock`); Read ports (`*Reader`)
  use **`get*`** (e.g. `getById`, `getByRoomAndTimeOverlap`); cross-module ExposeAPI read
  lookups use **`get*`** (renamed from legacy `find*`/`check*`, ADR 0010 §6). Adapter impl classes
  (`JpaXWriteAdapter`, `JooqXReadAdapter`) kept as-is.
- `docs/architecture/adr/0017-task-tailored-views-projection.md` — **Accepted**: CQRS query-side
  projections are task-specific views built on the read model; no generic DTO coupling.
- `docs/architecture/adr/0018-occupancy-contract-as-business-contract.md` — **Accepted (Lean &
  Clean — REVISED)**: System Buffer = **Operational Guardrail** (Ops), not a business contract.
  Core invariant: platform schedules room occupancy, not human activities. Buffer single knob
  `app.workshop.buffer.before-default-minutes`, snapshot immutable into `buffer_before_minutes`;
  **no** `max-minutes`, custom buffer, `ReBuffer`/`BufferJustification`, teaching window.
  Occupancy Window `[startTime - bufferBefore, endTime]`; overlap via existing
  `loadPublishedAndPlannedOverlappingWithLock` superset + in-memory filter; storage ceiling =
  DB `CHECK (buffer_before_minutes BETWEEN 0 AND 300)`.
- `docs/architecture/adr/0019-attendance-record-append-only-ledger.md` — **Accepted (REVISED v2)**:
  Attendance Record = append-only Decision Ledger. **4 semantics cốt lõi**: (1) `Workshop.state` là
  authority cho Attendance lifecycle (không suy diễn từ `startTime`/`endTime`); (2)
  `WorkshopCompleted.completedAt` là temporal anchor cho Reconciliation Window; (3) Student Appeal
  **không** đổi `currentResult` (chỉ request/evidence — chỉ `auditorAdjust()` là authoritative
  mutation); (4) Reconciliation Window là **Operational Setting** (default 24h), không phải Domain
  constant. Master `attendance_records` (`current_result` = materialized current state, `version` cho
  optimistic lock → 409) + Ledger `attendance_entries` (append-only, FK RESTRICT, khóa hợp phần
  `(record_id, entry_number)`). `beginReconciliation(completedAt)` qua `WorkshopCompletedIntegrationEvent`
  (outbox ADR 0011); role từ authenticated principal (không `X-Actor-Role`), state matrix §9.
- `docs/architecture/diagrams/` — sequence/flow diagrams (Mermaid).
- `docs/db/database.md` — authoritative database schema & design rules.
- `.llm/progress_log.md` — running history of completed work (local, git-ignored).

## Domain Rule: Room State (Static vs. Temporal)

- The `rooms` table stores ONLY the **physical/static state** of a venue: `ACTIVE`,
  `MAINTENANCE`, `DEACTIVATED`.
- **Temporal states** (`AVAILABLE`, `OCCUPIED`) are time-dependent and MUST NOT be persisted in the
  database.
- Dynamic availability is computed at runtime by intersecting a room's physical `ACTIVE` state with
  the scheduled `workshops` timeline (`start_time`, `end_time`, `state = 'PUBLISHED'`).
- Never introduce DB columns or entities that cache temporal room availability; this prevents
  concurrency/locking issues. Treat `docs/database.md` as the authoritative source for this rule.

## Domain Rule: Global / Set-based Invariant (Uniqueness) — Application Orchestration

- A **set-based invariant** (e.g. no two rooms share the same `(building, floor, code)` coordinate, and no two
  share the same `(building, floor, name)`) cannot be proven *by* a single Aggregate — it needs an arbiter that
  looks at the whole set. Per **ADR 0005 (Revised)**, this arbiter is the **Application handler**, which checks
  the invariant before delegating to the aggregate. The aggregate enforces ONLY local invariants (state transition,
  value consistency, etc.) and never receives a policy or repository parameter.
- **Mechanism:** handler queries the repository (`findByCoordinate`, `getByName`) → evaluates uniqueness →
  calls `Room.create(...)` with no policy argument. If the DB constraint catches a race, the adapter translates
  `DataIntegrityViolationException` → `DuplicateRoomCodeException` / `DuplicateRoomNameException`.
- The **DB unique constraints** (`uk_rooms_building_floor_code`, `uk_rooms_building_floor_name`) remain the
  authoritative, race-proof gate. The handler's read is a fast-fail / UX optimization; the adapter's
  `DataIntegrityViolationException` translation is the final backstop.
- **Guardrails:** never inject a Repository / outbound port into a domain object. Repository lives only in
  Application Handlers. Aggregate method signatures must not carry policy or repository parameters.
- **Handler role:** load aggregate → check global rules (query repository / ExposeAPI) → call aggregate
  (domain-only params) → save → publish events. Handler tests verify orchestration; domain tests verify local
  invariants only.
- **Reconstitution** (`Room.reconstruct`) bypasses all invariant checks (no spurious re-validation on read).
