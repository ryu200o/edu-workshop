# ADR 0003: Free-Form Room Name & Independent Integer Code

- **Status:** Accepted
- **Date:** 2026-07-19 (supersedes the earlier "Flexible Room Code & Opaque RoomName" framing)
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0001, ADR 0002, `.AGENTS.md`, `docs/db/database.md`, `docs/architecture/development-guidelines.md`

## Context

`RoomName` had grown to be a *derived* token: it was rendered from `(building, floor, code)` as
`F.0201` and reverse-parsed (or format-gated) on the read path. That coupling created three problems:

1. **Business owns the naming convention.** Rooms are frequently renamed for operational reasons
   (re-theming, reassignment) and the business accepts the risk of duplicates/format — the domain should
   not impose a brittle coordinate-derived format.
2. **`code` is only an FE concern.** The code is used solely for ordering rooms within a floor map; it has
   no business meaning and must change *silently* (no downstream module needs to react). Deriving it into
   the name made every code change also a "rename" event — wrong signal.
3. **One-way derivation is the wrong model.** Treating a name as a *consequence* of coordinates leaks
   coordinate logic everywhere and prevents the business from ever changing a name independently.

We therefore decouple the three concepts completely: `name` (free-form), `location` (building/floor,
legitimate abstraction), and `code` (independent integer).

## Decision

### 1. `name` is free-form
`RoomName` is a thin value object wrapping a single normalized string (trim + upper-case, non-blank). It
carries **no** `building`/`floor`/`code` fields and exposes **no** formatting regex. The only rule is the
non-blank invariant (RAM self-defense). The record is retained so future name rules can be added inside the
VO without touching callers.

- `of(String raw)` — the only factory. Blank check + normalize. No reverse-parse, no format gate.

### 2. `code` is an independent `int`
`Room.create(name, location, int code, capacity)` takes `code` directly. Changing it
(`Room.changeCode(int)`) is a **silent** mutation — it emits **no** domain event and no `RoomRenamedEvent`.
Validation: positive integer only.

### 3. Renaming changes `name` directly
`RenameRoomCommand(UUID, String newName)` → `Room.changeName(String)` emits
`RoomRenamedEvent(NAME_CHANGED)` carrying `oldName`/`newName` only (no code fields). Downstream modules
(e.g. Workshop, which snapshots `name`) can react. Name uniqueness is enforced by the DB
`uk_rooms_building_floor_name` constraint plus the race-proof gate in `JpaRoomWriteAdapter.save()`; the
handler does not pre-check it.

### 4. Relocation keeps `name` and `code`
`Room.relocateTo(RoomLocation)` moves only `location`; `name` and `code` are preserved (fully decoupled).
It emits `RoomRenamedEvent(LOCATION_CHANGED)` with `oldName == newName`.

### 5. Database
- `rooms.code` widened `VARCHAR(10)` → `INT` (Flyway `V3`).
- `uk_rooms_building_floor_code(building, floor, code)` retained (now `INT`).
- `uk_rooms_name` (name alone) dropped and replaced by `uk_rooms_building_floor_name(building, floor, name)`
  so two rooms in different locations may share a display name, scoped like the code constraint.

## Consequences

### Positive
- **Business-owned naming**: rename independently of coordinates, no format shackles.
- **Correct event signal**: code changes are silent; only genuine renames broadcast `NAME_CHANGED`.
- **Simpler VO**: `RoomName` is a single opaque string — no reverse-parse ambiguity, no coordinate leakage.
- **Location-scoped uniqueness**: same name allowed in different buildings/floors.

### Negative / Trade-offs
- Duplicate names *within* the same location are rejected by the DB constraint; the business must choose a
  unique name per physical location (acceptable — a location cannot host two identically-named rooms).
- `code` is now a plain integer with no format; FE must define its own ordering/rendering rules.
- `RoomName.matches(location)` and the coordinate factory are removed; any caller expecting a derived name
  must move to free-form input.

## Notes
- The write/update timestamp (`updatedAt`) is owned entirely by the aggregate in RAM and bumped on any
  mutating transition (including the silent `changeCode`), never by the persistence layer.
- Full suite green after this change (baseline + new/changed tests).
