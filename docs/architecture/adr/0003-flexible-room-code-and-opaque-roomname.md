# ADR 0003: Flexible Room Code & Opaque (Non-Reverse-Parsed) RoomName

- **Status:** Accepted
- **Date:** 2026-07-13
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0001, ADR 0002, `.AGENTS.md`, `docs/db/database.md`, `docs/architecture/development-guidelines.md`

## Context

The `RoomName` value object originally enforced a hard, brittle rule: a code of **exactly two digits**,
formatted as `[Building].[Floor][2-digit code]` (e.g. `F.201`). Two pressures forced a change:

1. **Business expressiveness** — rooms such as labs or themed spaces need alphanumeric codes
   (`LAB`, `01A`). We relaxed the code to **1–10 alphanumeric characters** (case-insensitive).
2. **Identity-collision safety at the floor boundary** — to remove ambiguity when buildings grow tall
   (floor ≥ 100), the floor in the display name is **zero-padded to a minimum of 2 digits**
   (`F`/5/`LAB` → `F.05LAB`, `F`/12/`05` → `F.1205`, `F`/105/`205` → `F.105205`).

A deeper architectural question then surfaced: should a raw room-name string be **reverse-parsed** back
into coordinates (`building` / `floor` / `code`) to drive write or constraint logic? Reverse-parsing is
fundamentally ambiguous — concatenating a variable-length digit `floor` with a variable-length
alphanumeric `code` has no unique split, and any lazy/greedy regex either *rejects every* canonical
zero-padded name or *mis-splits* it (`F.0201` → floor `0`/code `201`). Reverse-parsing also smuggles
coordinate logic into the read path (`GetRoomByNameQueryHandler`), weakening the one-way principle from
ADR 0001 (a name is a *consequence* of coordinates, never their source).

## Decision

### 1. Flexible, case-insensitive code + zero-padded floor display
`RoomName.asString()` renders `building + "." + String.format("%02d", floor) + code`. The code is
validated by `^[A-Za-z0-9]{1,10}$` and upper-cased; the building is already upper-cased by
`RoomLocation`. This removes the "2-digit-only" constraint and guarantees a stable, sortable,
collision-resistant display token.

### 2. `RoomName` is an OPAQUE display wrapper — no reverse-parse
`RoomName` now wraps a **single** already-normalized string (`value`). The regex that split a raw string
into `floor`/`code` is **deleted**. Two factories replace the old dual `of(...)` overloads:

- `of(RoomLocation location, String code)` — the **downward** (coordinate) path. The single source of
  truth for coordinates; it renders the string and retains the *forward-known* `building`/`floor`/`code`
  (set at construction, never reverse-parsed). This is what the write side and `matches()` use.
- `ofRaw(String raw)` — the **upward** (client query) path. It only **format-gates** the input
  (`^[A-Za-z0-9]+\.\d{2,}[A-Za-z0-9]{1,10}$`, blank/format rejection, upper-case) and stores the string.
  For raw names the coordinate fields stay `null` — they are **opaque** and must never be read as
  coordinates.

`equals`/`hashCode` are based solely on `value`. `asString()` returns `value`.

### 3. Type-safe, exact-match lookup (no coordinate split at query time)
`RoomQueryPort.findByName(RoomName name)` is **type-safe** on the RAM side. The infrastructure adapter
(`JpaRoomAdapter`) unwraps only `name.asString()` and runs an **exact** `WHERE name = ?` match against the
DB index — the high-performance, collision-proof strategy. The query handler validates input via
`RoomName.ofRaw(...)` but never reconstructs coordinates from it.

### 4. Database: widen the `code` column only — schema shape unchanged
Flyway `V2__alter_room_code_flexibility.sql` alters `rooms.code` from `VARCHAR(2)` to `VARCHAR(10)`
(`ALTER TABLE rooms ALTER COLUMN code SET DATA TYPE VARCHAR(10)`, portable across H2-PostgreSQL and
PostgreSQL). The `code` column and the composite `(building, floor, code)` uniqueness constraint remain;
the floor is **not** stored as a string — only the rendered `name` carries zero-padding. No temporal
state is introduced (per ADR 0001).

## Consequences

### Positive
- **Zero identity collisions**: the unique `name` (exact match) plus the composite `(building, floor, code)`
  guard the write side; reverse-parsing ambiguity is gone from the read side.
- **One-way principle fully realized**: coordinates → name is the only derivation; raw strings are display-only.
- **Type safety on RAM** (`findByName(RoomName)`) without sacrificing the exact-match DB query.
- **Flexible, human-friendly names** (`LAB`, `01A`) with stable zero-padded floors.
- **Minimal DB change** (one column widen); no locking, no new temporal state.

### Negative / Trade-offs
- A raw `RoomName` is intentionally *coordinate-blind*: callers must not treat `ofRaw` names as having
  `floor`/`code` (they are `null`). This is by design and is asserted by tests.
- `matches(RoomLocation)` is meaningful only for coordinate-built names; the read path does not use it.
- The previous `of(String)` overload (reverse-parse) is removed — any external caller must move to
  `ofRaw` (display) or `of(location, code)` (coordinate).

## Notes
- Validation split: **code format** is enforced where the code is a first-class input (`of(location, code)`
  via `normalizeCode`); **display format** is enforced where a raw string arrives (`ofRaw` via `DISPLAY_FORMAT`).
- This change is behavior-preserving for the write/constraint model; it only widens the allowed code space
  and changes the *visual* floor width. Full suite: 48/48 green.
