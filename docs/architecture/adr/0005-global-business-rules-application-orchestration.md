# ADR 0005: Global Business Rules — Application Orchestration, Not Domain Policy Injection

- **Status:** Revised (2026-07-27) — supersedes the earlier domain-policy-injection framing; refactor completed
- **Date:** 2026-07-27 (revised); original 2026-07-12
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0002, ADR 0003, ADR 0004, `AGENTS.md`, `docs/db/database.md`

## Context

The original ADR 0005 (now superseded) introduced a `RoomUniquenessPolicy` — a **Domain Policy interface** — that was passed **into the aggregate** on every uniqueness-sensitive operation:

```java
Room.create(id, name, location, code, capacity, now, policy);       // old
room.changeCode(newCode, policy, now);                               // old
room.relocateTo(newLocation, policy, now);                           // old
room.changeName(newName, policy, now);                               // old
```

The policy was considered a "domain-owned business rule" wrapped around a set-based query. The aggregate received it as a compile-time-pure interface — the IO lived in the infrastructure adapter.

### Why this approach is being superseded

Despite the theoretical purity (domain depends only on a domain interface), injecting a policy/repository surrogate into the aggregate creates several problems:

1. **Aggregate API pollution.** Mutation signatures now carry a non-domain parameter (`RoomUniquenessPolicy policy`) that has nothing to do with the *intent* of the operation. Every caller must satisfy this dependency.

2. **Test complexity.** Aggregate tests need a stub policy (`ALWAYS_UNIQUE`) even for operations that have nothing to do with uniqueness. The test doubles multiply with every new policy.

3. **Slippery slope.** If "set-based invariant" justifies injecting a query-capable interface, then every cross-aggregate rule could argue the same — leading to a domain cluttered with IO interfaces.

4. **Violation of aggregate autonomy.** An aggregate should enforce invariants it can prove with data it already holds. Outsourcing invariant evaluation to an injected interface blurs the aggregate's responsibility boundary.

5. **False dichotomy.** The earlier ADR framed the choice as "Domain policy" vs "Application guard". In practice, the **Application orchestration** pattern (`check → aggregate.execute() → save`) is simpler, more testable, and respects the aggregate boundary without ceremony.

### The Workshop module as validation

The Workshop module (built after this lesson was learned) already follows the correct pattern:

```java
// PublishWorkshopCommandHandler — Application orchestrates global rules:
RoomPlanningPermission permission = roomExposeApi.checkPlanningPermission(roomId);
// ... application-level check ...
int overlapping = workshopRepository.countOverlapping(...);
// ... application-level check ...
workshop.publish(now, actualRoomCapacity);   // aggregate only checks LOCAL invariants
```

The aggregate (`Workshop.publish`) only validates:
- State machine transition (PLANNED → PUBLISHED)
- Capacity ≤ room capacity (local invariant from its own `RoomReference` snapshot)

It never receives a policy or repository.

## Decision

### 1. Aggregate enforces ONLY local invariants

An aggregate validates only what it can prove with its own data:

- State machine transitions (`requireState`)
- Value object consistency (`endTime > startTime`)
- Local business rules (capacity within snapshot, not-null guards)
- Snapshot integrity

The aggregate method signature must NOT carry parameters that are:
- Repository interfaces
- Policy interfaces backed by IO
- Query/reader interfaces
- Any form of outbound port surrogate

### 2. No Repository or Policy injection into Aggregate (overruling old ADR 0005)

The old pattern `aggregate.method(..., policy)` is **prohibited** in the target architecture.

| Subject | Old approach (rejected) | New approach (adopted) |
|---|---|---|
| Room uniqueness | `Room.create(..., policy)` — aggregate calls policy | Handler calls `repository.findByCoordinate()` / checks DB constraint |
| Workshop overlap | Domain did not exist yet | Handler calls `repository.countOverlapping()` |
| Room planning permission | Not applicable | Handler calls `roomExposeApi.checkPlanningPermission()` |

### 3. Global business rules belong to Application orchestration

Rules that require observing the aggregate set (uniqueness, overlap, existence) are handled by the Application layer using the **check-then-execute** pattern:

```
 ┌─────────────────────────────────────────┐
 │           Application Handler            │
 │                                          │
 │  1. Load aggregate from repository        │
 │  2. Check global rules (query/repo)       │
 │  3. Call aggregate behavior (local only)  │
 │  4. Save                                  │
 │  5. Publish events                        │
 └─────────────────────────────────────────┘
```

The handler decides whether the global context permits the operation. The aggregate decides whether its own invariants allow it.

#### Exception: Domain Service for genuine domain concepts

A Domain Service may exist only if it represents an **independent business concept** that doesn't naturally belong to any single aggregate — for example, a `PricingCalculator`, `AllocationStrategy`, or `SchedulingAlgorithm`. A Domain Service must NOT be created solely to wrap a repository query or policy check.

### 4. Repository appears only in Application

`RoomRepository`, `WorkshopRepository`, and all outbound ports are injected into Application handlers only. They never appear in:

- Aggregate method signatures
- Value object constructors
- Domain Service constructors (unless the service is a genuine domain concept)
- Policy interfaces (removed)

### 5. DB unique constraints remain the authoritative gate

DB unique constraints (`uk_rooms_building_floor_code`, `uk_rooms_building_floor_name`) are the final integrity authority. The Application-level check is a fast-fail UX optimization. `JpaRoomWriteAdapter.save()` still translates `DataIntegrityViolationException` to domain exceptions for the TOCTOU race window.

## Consequences

### Positive

- **Clean aggregate API.** Mutation signatures carry only what the operation semantically needs.
- **Simpler tests.** Aggregate tests need no policy stubs — pure domain setup.
- **Clearer responsibility.** Application orchestrates; aggregate decides. No ambiguity.
- **Workshop module is already compliant.** No refactor needed for Workshop.
- **Stronger boundary.** Repository/IO stay firmly in the outer (Application) ring.

### Negative / Trade-offs

- **Handlers are slightly heavier.** They contain the orchestration logic for global rules. Accepted — orchestration *is* the handler's job.
- **No single "chokepoint"** for uniqueness checking. Each handler must independently call the appropriate repository method. Mitigated by DB constraints as final authority.

## Implementation Status

The Room module has been fully refactored to the check-then-execute pattern:

| What | Status |
|---|---|
| `Room.create(..., RoomUniquenessPolicy)` → clean signature | ✅ Completed |
| `Room.changeCode(..., RoomUniquenessPolicy)` → clean signature | ✅ Completed |
| `Room.changeName(..., RoomUniquenessPolicy)` → clean signature | ✅ Completed |
| `Room.relocateTo(..., RoomUniquenessPolicy)` → clean signature | ✅ Completed |
| All 4 handlers check uniqueness via `existsBy*` before calling aggregate | ✅ Completed |
| `RoomUniquenessPolicy` interface removed | ✅ Completed |
| `JpaRoomUniquenessPolicy` adapter removed | ✅ Completed |
| `domain/model/policy/` package removed | ✅ Completed |
| All tests updated (no policy stubs) | ✅ Completed |
| Exception hierarchy corrected (Duplicate* → ApplicationException) | ✅ Completed |

## Notes

- ADR 0002 is updated to align with this decision — see its Implementation Gap and added sections.
- The old ADR 0005 (domain-policy-injection framing) is superseded. Its text is not retained; this document is the authoritative version.
- Fitness function (future ArchUnit): no domain class may import from `application`, `port`, or infrastructure packages; no aggregate method may accept an interface from `domain/model/policy/`.
