# ADR 0001: Isolate Room Static and Temporal States

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Room Module / Architecture Guild
- **Related:** `docs/db/database.md`, `AGENTS.md` (Domain Rule: Room State)

## Context

The platform needs to know whether a physical room (venue) can host a workshop. Two very different
kinds of "availability" are easily confused:

1. **Static / Physical State** — the *intrinsic* operating condition of the venue itself, owned
   exclusively by the Room module: `ACTIVE`, `MAINTENANCE`, `DEACTIVATED`.
2. **Temporal State** — time-dependent availability such as `AVAILABLE` or `OCCUPIED`, which depends
   on the scheduled `workshops` timeline (`start_time`, `end_time`, `state = 'PUBLISHED'`).

Storing temporal availability inside the `rooms` table would force the Room module to know about
workshop schedules and would require updating `rooms` on every booking/rescheduling. Under concurrent
booking traffic this causes row-lock contention on the `rooms` table — a critical hotspot for a
platform whose primary write path is registration.

## Decision

The Room module **owns and persists ONLY the static/physical state** of a venue. It:

- Exposes the physical state (`ACTIVE`, `MAINTENANCE`, `DEACTIVATED`) and related physical attributes
  (`name`, `capacity`, `location`) via its public `RoomExposeAPI`.
- **Never** models, stores, or computes temporal availability (`AVAILABLE`/`OCCUPIED`).
- Is **not responsible** for, and has no dependency on, the scheduling/temporal concerns of other
  modules (e.g. Workshop).

Dynamic availability is computed at runtime by the *consumer* module (Workshop) by intersecting a
room's physical `ACTIVE` state with the published workshop timeline. Cross-module communication uses
logical UUID references and the `module_api` driving adapter — no shared temporal state.

This is enforced as a hard architectural rule in `AGENTS.md` and as the schema source of truth in
`docs/db/database.md` (the `rooms.state` column).

## Consequences

### Positive
- The `rooms` table is write-light: it changes only on genuine physical-state transitions, eliminating
  a major lock-contention hotspot.
- The Room module has a small, stable, well-bounded responsibility (Information Hiding boundary).
- Temporal logic lives where the timeline data lives (Workshop), keeping each module cohesive.

### Negative / Trade-offs
- Consumers must compute availability at runtime; this logic is duplicated per consumer unless a shared
  read model is introduced later.
- A room can be `ACTIVE` physically yet temporally `OCCUPIED`; callers must always combine both views
  and must not assume `ACTIVE` implies `AVAILABLE`.

## Validation

See `docs/architecture/diagrams/room-workshop-publish-flow.mermaid`: the Workshop module queries the
Room module for *physical* info only, then applies its own temporal/scheduling logic.
