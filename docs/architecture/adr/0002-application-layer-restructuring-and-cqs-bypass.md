# ADR 0002: Application Layer Restructuring & CQS Bypass

- **Status:** Revised (2026-07-25) — updated with Application orchestration rules, repository boundaries
- **Date:** 2026-07-25 (revised); original 2026-07-12
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** `docs/architecture/development-guidelines.md`, ADR 0001, ADR 0005, ADR 0006, ADR 0010, `AGENTS.md`

## Context

The initial per-module skeleton placed the Application layer as `application/{command, query, port/inbound,
port/outbound, event}` and there was a temptation to host outbound ports under a `domain/spi` location.
As we prepare to actually implement the Application layer (Command/Query handlers, buses), we need a
single, unambiguous "golden" structure that all modules follow, aligned with the reference in
`development-guidelines.md`, and compatible with Spring Modulith's boundary enforcement.

Later experience with the Workshop module and the refactoring of ADR 0005 (global business rules)
added further architectural constraints that are now codified here.

## Decision

### 1. Golden Application structure (per module)
```
application/
├── port/
│   ├── inbound/
│   │   ├── command/   (write DTOs as records + CommandBus interface)
│   │   └── query/     (read DTOs/projections as records + QueryBus interface)
│   └── outbound/      (ALL outbound ports / SPI owned by the module)
├── handler/           (fully flattened — no sub-packages)
├── event/             (application-level events)
└── mapper/            (DTO <-> Domain converters, when needed)
```

### 2. Reject `domain/spi`; outbound ports live in `application/port/outbound/`
Outbound ports (SPI) are an Application concern (they express what the use cases need from the
outside world), not a Domain concern. We **reject any `domain/spi` package** and place **all**
outbound ports in `application/port/outbound/`. This keeps the Domain core pure (no port dependencies),
consistent with ADR 0001 and the "clean domain" outcome of the Room rework.

### 3. Flatten `handler/` with package-private visibility
All command/query handlers and the bus implementations are placed **flat** in `application/handler/`
with **no sub-packages**, and are declared **package-private** (no `public`). Other modules therefore
cannot import or interfere with them — the only exposed surfaces are the `CommandBus`/`QueryBus`
interfaces in `port/inbound/` and the module's `ExposeAPI`.

### 4. CQRS Bypass for the read side
Queries do **not** pass through the Domain Model. A query handler calls an outbound **query gateway**
that returns a response/projection DTO directly (read-optimized, `@Transactional(readOnly = true)`),
bypassing aggregate reconstruction. Commands keep going through the Domain Model (rich behavior +
invariants). Read and write outbound ports are separated (e.g. `RoomRepository` vs
`RoomReader`), enabling future read/replica splitting without touching the Application layer.

> **Update (Room module):** the read side is implemented with **JOOQ** (`JooqRoomReadAdapter`), querying the
> `rooms` table directly via generated type-safe table classes and mapping flat columns into `Room*View`
> projections — no JPA entity, no domain reconstruction. The write side stays on **JPA**
> (`JpaRoomWriteAdapter`); both share one datasource (logical C/Q split). See `development-guidelines.md` §3.6.

### 5. "Intentional duplication" — each module owns its own Command/Query Bus
> **Superseded by ADR 0006.** As of ADR 0006, the bus capability (interface, dispatcher, immutable
> registry, behavior-chain pipeline) moves into the Shared Application Kernel; modules keep only
> Commands/Queries/Handlers. The boundary-protection intent of this section is now achieved via
> pluggable `CommandBehavior` extension points rather than duplicated bus classes. The text below is
> retained for historical context.
Rather than sharing a single global bus, **each module declares its own `CommandBus`/`QueryBus`**
(and the `Command`/`Query`/`*Handler` shared framework interfaces per module). This intentional
duplication protects Spring Modulith boundaries: no module reaches across into another module's
application internals, and the buses resolve handlers only within the owning module's context.

### 6. Aggregate enforces ONLY local invariants

An aggregate validates only what it can prove with its own data:

- State machine transitions (`requireState`)
- Value object consistency (`endTime > startTime`)
- Local business rules (capacity within snapshot, not-null guards)
- Snapshot integrity

An aggregate method must **never** accept a repository, policy, or any IO-backed interface as a parameter.
Global / set-based rules (uniqueness, overlap, existence) belong to Application orchestration.

**Workshop module (reference implementation):**
```java
// Workshop.publish(Instant now, int actualRoomCapacity)
//   → only validates state transition + local capacity invariant
//   → NO policy, NO repository parameter
```

**Room module (needs refactor — see Implementation Gap):**
```java
// Room.create(..., RoomUniquenessPolicy policy)  ← REJECTED pattern
// Room.changeCode(..., RoomUniquenessPolicy)      ← REJECTED pattern
```

### 7. Global business rules — Application orchestration (check-then-execute)

Rules that require observing the aggregate set are handled by the Application layer:

```
 ┌──────────────────────────────────────────────────┐
 │                Application Handler                 │
 │                                                    │
 │  1. Load aggregate from repository                  │
 │  2. Check global rules (query repository / ExposeAPI)│
 │  3. Call aggregate behavior (local invariants only)  │
 │  4. Save aggregate                                  │
 │  5. Publish events                                  │
 └──────────────────────────────────────────────────┘
```

**Workshop examples (already compliant):**

- `ScheduleWorkshopCommandHandler`: loads workshop → calls `roomExposeApi.checkPlanningPermission()` → calls `workshop.schedule(roomRef)` → saves
- `PublishWorkshopCommandHandler`: loads workshop with lock → calls `roomExposeApi.checkPlanningPermission()` → calls `repository.countOverlapping()` → calls `workshop.publish()` → saves

The check and the execute are separate steps, both in the Application handler.
The aggregate never sees the repository or the permission check.

### 8. Repository appears only in Application

`RoomRepository`, `WorkshopRepository`, and all outbound ports are injected **only** into Application handlers. They must never appear in:

- Aggregate method signatures
- Value object constructors
- Domain Service constructors (unless the service is a genuine domain concept — see §9)
- Policy interfaces (removed per ADR 0005)

Cross-module queries (e.g., Workshop calling Room) go through `RoomExposeAPI` (per ADR 0010), never through a repository.

### 9. Domain Service — only for genuine domain concepts

A Domain Service may exist only if it represents an **independent business concept** that doesn't naturally belong to any single aggregate — for example, a `PricingCalculator`, `AllocationStrategy`, or `SchedulingAlgorithm`.

A Domain Service must NOT be created solely to:
- Wrap a repository query
- Wrap a uniqueness check
- Hide a policy interface
- Perform any IO or cross-module call

If the "service" has no business logic of its own and only delegates to a repository, keep it as an Application handler concern.

### 10. Cross-module contract — RoomExposeAPI as business capability

Per ADR 0010, cross-module dependencies are Application-only. The Application handler calls `RoomExposeAPI` to get a business decision (planning permission, room data). The Domain never imports another module's API or DTOs. The handler maps contract DTOs to domain VOs (Anti-Corruption Layer).

### 11. Event as integration mechanism, not runtime validation

Domain events are an **integration mechanism** for:
- Synchronization across modules
- Read model projection
- Choreography (event-driven workflows)

Events must NOT replace runtime validation. For example, a `WorkshopPublished` event does not replace the runtime overlap check — the overlap is checked synchronously in the handler before the aggregate is called. The event is published **after** all checks pass, as a notification to other modules.

## Consequences

### Positive
- One canonical structure across modules; `create-module.sh` scaffolds it automatically.
- Pure Domain (no SPI leakage, no policy injection), hidden handlers (package-private), fast reads (CQS bypass).
- Strong module isolation for Spring Modulith via per-module buses.
- Clear responsibility: Application orchestrates global rules, aggregate owns local invariants.
- Workshop module serves as the reference — no refactor needed.

### Negative / Trade-offs
- Some boilerplate is duplicated per module (buses + framework interfaces) — accepted deliberately.
- Read and write models can diverge; developers must keep query projections in sync with schema.
- Room module handlers need refactoring to remove `RoomUniquenessPolicy` from aggregate calls (see Implementation Gap below).
- Handlers are slightly heavier with orchestration logic (accepted — that is their job).

## Implementation Gap

The Room module still uses the old pattern (policy injected into aggregate). The following must be refactored to comply with §6–§8:

| What | Current (gap) | Target (ADR compliant) |
|---|---|---|
| `Room.create(..., RoomUniquenessPolicy)` | Aggregate checks policy | Handler checks uniqueness, then calls `Room.create()` |
| `Room.changeCode(..., RoomUniquenessPolicy)` | Same | Same |
| `Room.changeName(..., RoomUniquenessPolicy)` | Same | Same |
| `Room.relocateTo(..., RoomUniquenessPolicy)` | Same | Same |
| `CreateRoomCommandHandler` | Injects policy, passes to aggregate | Injects `RoomRepository`, checks before create |
| `RenameRoomCommandHandler` | Same | Same |
| `RelocateRoomCommandHandler` | Same | Same |
| `ChangeRoomCodeCommandHandler` | Same | Same |
| `RoomUniquenessPolicy` interface | Exists in `domain/model/policy/` | Remove entirely |
| `JpaRoomUniquenessPolicy` | Infrastructure adapter | Remove entirely |
| `domain/model/policy/` package | Contains `RoomUniquenessPolicy` | Delete package |

The Workshop module is fully compliant and requires no changes.

## Notes
- ADR 0005 (revised) contains the full rationale for removing policy injection from the aggregate.
- This revision supersedes the Refinement Note that previously referenced the old ADR 0005.
