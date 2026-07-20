# ADR 0007: Cross-Module Data Decoupling via Selective Metadata Snapshotting

- **Status:** Proposed
- **Date:** 2026-07-20
- **Deciders:** Lead Engineer / Technical Architect
- **Related:** `docs/db/database.md` (§3 `workshops`, §4 `workshop_histories`, §5 `workshop_snapshots`),
  ADR 0001 (Room static vs temporal state isolation), `docs/architecture/diagrams/room-workshop-publish-flow.mermaid`,
  `docs/architecture/diagrams/room-workshop-event-reaction.mermaid`, `AGENTS.md`

---

## Context

The platform is being built as a **Modular Monolith** whose primary goals are module autonomy and low
coupling. Each module (Room, Workshop, Registration) owns and manages its own tables; Spring Modulith
enforces the allowed dependencies between them.

When designing the `Workshop` module, the read side (UI / query layer) must display a workshop list that
includes the room's human-readable identity — its display name and physical location. A traditional,
3NF-normalized design would store only `room_id` in `workshops` and rely on a **physical `FOREIGN KEY` +
cross-module `JOIN`** to fetch the room's current data at read time.

That approach is rejected because it:

- **Violates the database-level isolation boundary** — Workshop would be physically coupled to Room's
  schema, breaking module autonomy and Spring Modulith's allowed-dependency graph.
- **Corrupts historical integrity** — if Room renames or relocates a room, every historical workshop
  row would silently reflect the new name instead of the name that was valid at schedule/publish time.
- **Blocks the microservice extraction path** — a cross-module JOIN cannot survive a future split into
  independent deployable services without rewriting the SQL.

This ADR records the chosen alternative: **Selective Metadata Snapshotting** at the Workshop live table.

---

## Decision

The team adopts the **Selective Metadata Snapshotting** pattern at the `workshops` live table to keep the
read side self-contained while preserving module boundaries.

1. **No physical foreign key, no cross-module JOIN.** The relationship between Workshop and Room is
   maintained purely as a **logical reference**: the `room_id` column of type `UUID`. There is no FK
   constraint and no SQL `JOIN` across module tables. This is consistent with the project-wide
   "Logical References (Zero Cross-Module Foreign Keys)" principle in `docs/db/database.md`.

2. **Selective snapshot columns on `workshops`.** The `workshops` table carries two denormalized
   snapshot columns, filled from Room at schedule time and refreshed at reschedule time:
   - `room_name_snapshot VARCHAR(255)` — the room's display name at scheduling time.
   - `room_location_snapshot VARCHAR(255)` — the room's physical location (building/floor) at scheduling
     time.

   These are **nullable in the database** (a `DRAFT` workshop may exist before a room is assigned) and are
   **enforced non-null by the domain aggregate** before the workshop transitions to `PUBLISHED`.

3. **Two synchronization paths:**
   - **Proactive (now):** when a workshop is `schedule()`d / `publish()`ed, the Workshop module pulls the
     current room data via `RoomExposeAPI` (get physical room info) and writes it into the snapshot
     columns. The read side then serves a complete UI row from a **single-table query** with no runtime
     call to Room.
   - **Reactive (deferred):** when Room renames / relocates a room in the future (once the Event Bus /
     Outbox infrastructure is enabled), Room publishes a domain event; the Workshop `RoomEventHandler`
     listens and refreshes the snapshot columns for workshops still in a non-terminal state (`PUBLISHED`).
     Room events are currently **recorded-only** (not yet published), so this path is deferred — see
     ADR 0001 / progress log entry 048.

4. **Freezing on completion:** when a workshop reaches `COMPLETED`, its final state (including the room
   name/location) is copied into the immutable report table `workshop_snapshots`, permanently freezing the
   historical record.

---

## Consequences

### Positive (Pros)
- **Module Autonomy:** the Workshop read side is self-sufficient. One single-table query fully serves the
  UI without touching or calling the Room module at runtime.
- **Historical Integrity:** historical data is never silently mutated when Room renames or deletes a room;
  each workshop keeps the snapshot captured at schedule/publish time, and `workshop_snapshots` freezes it
  on completion.
- **Migration Path:** the design is ready to be extracted into independent microservices on demand, with no
  SQL rewrite, because no cross-module JOIN or FK exists.

### Negative / Trade-offs (Cons)
- **Data Redundancy:** a few extra `VARCHAR` columns are stored per workshop. The storage cost is
  negligible against modern hardware and is an accepted price for autonomy.
- **Eventual Consistency:** the room name shown on a workshop may lag briefly before it is resynchronized
  via the reactive Room event. This short inconsistency window is accepted; for the proactive path the
  snapshot is correct at schedule/publish, and the reactive path keeps `PUBLISHED` workshops in sync once
  the Event Bus is enabled.

---

## Sync Procedure (summary)

1. **On `schedule()` / `publish()`:** Workshop proactively calls `RoomExposeAPI` to fetch the current room
   data and fills the snapshot columns.
2. **On Room data change (future):** when the Event Bus is ready, Room emits a domain event; Workshop's
   `RoomEventHandler` listens and refreshes the snapshot columns for non-terminal (`PUBLISHED`) workshops.
3. **On `COMPLETED`:** the data is transferred into the immutable `workshop_snapshots` report table,
   freezing the history permanently.

---

## Validation / Future Work

- The schema in `docs/db/database.md` §3–§5 already implements this decision (nullable snapshot columns,
  logical `room_id` UUID, no cross-module FK; `workshop_histories` and `workshop_snapshots` are same-module
  tables with physical FK to `workshops(id)` `ON DELETE CASCADE`).
- Before the reactive sync path can ship, the Room module must publish its recorded events
  (`RoomRenamedEvent`, `RoomLocationChanged`) through the Event Bus / Outbox — currently out of scope for
  the Workshop phase-1 core flow (CREATE → SCHEDULE → PUBLISH).
