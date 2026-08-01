# ADR 0008: Room Allocation Policy — Planning (PLANNED) vs Reservation (PUBLISHED)

- **Status:** Proposed
- **Date:** 2026-07-20
- **Deciders:** Lead Engineer / Technical Architect
- **Related:** `docs/db/database.md` (§3 `workshops`, §5 `workshop_snapshots`),
  ADR 0001 (Room static vs temporal state isolation), ADR 0007 (Cross-Module Data Decoupling via Selective
  Snapshotting), `docs/architecture/diagrams/room-workshop-publish-flow.mermaid`, `.AGENTS.md`
  (Exception Layering, Domain purity).

> **Mode:** PLAN ONLY. This ADR is a business-policy decision record. It introduces **no Java code, no
> migration, no API change, no test**. It guides future Workshop Application / Query / UI implementation.

---

## Context

During Workshop domain discovery we refined an important business rule that affects the entire Workshop
lifecycle, Room availability queries, and future UX. This is **not a technical optimization** — it is a
business policy that must be agreed and documented before any implementation touches the Application or
Query layers.

The current Workshop lifecycle (per `.llm/current_plan.md` v2) is:

```
DRAFT  ──plan()──▶  PLANNED  ──publish()──▶  PUBLISHED
```

The semantic meaning of `PLANNED` has now been refined: **scheduling plans a room; publishing reserves it.**
This distinction was implicit (and the publish-flow conflict check in `room-workshop-publish-flow.mermaid`
could be read as a plan-time gate). This ADR makes the policy explicit so the future Application and Query
implementations converge.

---

## Decision

### 1. Planning does NOT reserve a room

When a workshop enters `PLANNED`:

- Only `room` (via `RoomReference`) is assigned. `startTime`, `endTime`, `capacity` are already known at
  creation time (DRAFT), so they are not re-validated at `plan()` — only room non-null is enforced.

However, `PLANNED` **does NOT grant exclusive ownership** of the room. Multiple workshops may legitimately
exist in `PLANNED` for the **same room and overlapping time window**.

Example (VALID):

```
Workshop A   PLANNED   Room 201   09:00–11:00
Workshop C   PLANNED   Room 201   09:00–11:00
```

Both are allowed. Planning is a *planning* act, not a *reservation*.

### 2. Publishing reserves the room

Only a `PUBLISHED` workshop owns the room for a given time window. At `publish()` time, the **Application
layer** performs Room availability verification (via `RoomExposeAPI` / its own overlap scan):

- If another workshop is already `PUBLISHED` for the same room and overlapping time → **publish fails**.
- The aggregate remains **unchanged** (the conflict is detected outside the aggregate, before the transition).
- The conflict is **NOT** checked during `plan()`.

This keeps the aggregate pure: it owns local invariants and the state machine, but never performs cross-
workshop / room-occupancy lookups.

### 3. Room Availability has three semantic states

The future Room Availability query must distinguish three states (not a binary available/occupied):

| State | Meaning | Scheduling allowed? | Publish possible? | UI treatment |
|-------|---------|---------------------|-------------------|--------------|
| **A — AVAILABLE** | No published workshop. No planned workshop. | Yes | Yes | Normal selectable room. |
| **B — AVAILABLE_WITH_PLANNING_CONFLICT** | No published workshop. One or more `PLANNED` workshops already plan to use the room (same/overlapping time). | **Yes** (planning info only, does NOT block) | Yes (unless a published conflict appears at publish time) | Show room available **+ warning** + list/count of planned workshops. |
| **C — OCCUPIED** | A `PUBLISHED` workshop owns the room for the window. | **No** | **No** (impossible) | Room disappears from available-room selection. |

State B is **planning information only** — it surfaces contention without pessimistic locking.

### 4. UX rationale

Users should be informed of planning contention without being prevented from collaborating.

Example:
```
Room 201
✓ Available
⚠ Planned by:
  - Workshop A
  - Workshop C
```
The user can still choose Room 201, but understands another planner is already considering it. This
**intentionally favors transparency over pessimistic locking**.

### 5. Responsibilities

**Workshop Aggregate** is responsible for:
- local invariants (`plan()` validation: room/time/capacity),
- lifecycle transitions and the state machine (`DRAFT → PLANNED → PUBLISHED`),
- recorded domain events.

It is **NOT** responsible for:
- room occupancy lookup,
- published-conflict detection,
- room reservation,
- cross-workshop queries.

**Application layer** is responsible for:
- Room availability lookup (overlap scan across `workshops`, excluding self on reschedule),
- publish-time conflict verification (fail `publish()` if a `PUBLISHED` conflict exists),
- orchestration with `RoomExposeAPI` (Plan C Inversion of Control — Room owns the conflict policy).

**Query Model (future)** should expose planning information. The read model must be able to represent:
- *occupied by a published workshop*, and
- *planned by workshops in `PLANNED` state*,
as **distinct** concepts (State C vs State B), not a single boolean.

---

## Consequences

### Positive (Pros)
- **Better UX** — planners see contention early instead of hitting a hard failure at publish.
- **No pessimistic locking** — no cross-workshop lock on `workshops`; `plan()` stays lightweight.
- **Parallel planning** — multiple organizers may prepare workshops for the same room concurrently.
- **Explicit business semantics** — planning vs reservation is a first-class, documented distinction.
- **Aggregate stays pure** — no IO / cross-module lookup leaks into the domain.

### Negative / Trade-offs (Cons)
- **Publish may fail after a successful plan.** A workshop can be `PLANNED` (valid) yet fail to be
  `PUBLISHED` if another workshop publishes the same room first.
- **Re-planning cost.** A user may need to re-plan (reschedule / change room) if another workshop publishes
  first.
- **Richer availability query.** The Room Availability read model must compute and represent three states
  instead of a binary flag.

These trade-offs are **accepted** as the price of transparency-first planning.

---

## Validation / Future Work

- This ADR does not change `docs/db/database.md` (no schema/migration). The `workshops` table already supports
  the lifecycle; the `idx_workshop_room_time` index serves the Application-layer overlap scan.
- Future Workshop **Application** slice: `publish()` handler performs the conflict check (via
  `WorkshopRepository` overlap scan + `RoomExposeAPI.checkAvailability` per Plan C) and fails *before* calling
  `workshop.publish()`.
- Future Workshop **Query** slice: a Room Availability query/projected View returns State A / B / C with the
  planned-workshop list for State B.
- Cross-module note: Room events are currently **recorded-only** (not published), so reactive snapshot refresh
  (ADR 0007) remains deferred; this ADR's policy is independent of that and can ship without the Event Bus.

## Eviction (Phase 3)

When a `PUBLISHED` workshop claims a time window occupied by `PLANNED` workshops, those `PLANNED` workshops are
evicted to `DRAFT` via `evictPlanningOnConflict`. This differs from `returnToDraft` (used by `DELETE /plan` and
room deactivation):

| Method | Room reference | Maintenance warning | Time window | Domain event |
|---|---|---|---|---|
| `evictPlanningOnConflict` | **Kept** | **Kept** | **Kept** | None (internal side effect) |
| `returnToDraft` | Cleared (`null`) | Cleared (`false`) | Kept | `WorkshopUnplanned` |

Eviction keeps the room so the admin can adjust the time on the retained window and re-plan without re-selecting a room.
