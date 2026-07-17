# ADR 0004: Room Rename Use Case & CQS Driving-Adapter Hardening (Hybrid CQS)

- **Status:** Accepted
- **Date:** 2026-07-13
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0001, ADR 0002, ADR 0003, `.AGENTS.md`, `docs/architecture/development-guidelines.md`,
  `docs/db/database.md`

## Context

Branch `feat/room-rename-usecase` delivered two things that together upgraded the Room module from a
"working slice" to a **reference implementation** of our architectural rules:

1. **A real write use case — Direct Rename** (`room.changeCode`): change only the `code`, keep
   `building`/`floor` frozen, recompute the one-way `RoomName`, and emit a rich `RoomRenamedEvent`.
2. **A hardening pass on the HTTP driving adapter** following CQS + Spring Modulith:
   - split the generic `RoomResponse` into Command-side `Result` DTOs and Query-side `View` DTOs;
   - split the monolithic `RoomController` into `RoomCommandController` + `RoomQueryController`;
   - centralized Room-specific error handling in a scoped `@RestControllerAdvice`;
   - then re-packaged into **Hybrid CQS** — nested `Command.Result` (1-1 locality) and a dedicated
     `port.in.query.view` package for the multi-query `View` types.

This ADR records the decisions so the patterns can be replicated by every module without re-deriving them.

## Decision

### 1. Rename = change code only; building/floor are immutable
`Room.changeCode(String newCode)` keeps the `RoomLocation` unchanged, recomputes the name via
`RoomName.of(location, newCode)` (preserving the one-way coordinate→name derivation from ADR 0003), and
records a `RoomRenamedEvent`. It is **idempotent** when the new code equals the current code (no event,
no `updatedAt` bump) and **rejects a `DEACTIVATED` room** (frozen-permanent, consistent with ADR 0001).

### 2. Multi-tier guard, in performance order (write side)
`RenameRoomCommandHandler` enforces: **load aggregate** (write port) → **RAM guard** (the `RoomName` VO
self-validates/normalizes the new code) → **idempotency skip** (same code ⇒ no gate, no persist) →
**DB gate** (`RoomRepository.existsByCoordinate(building, floor, code)` against the
*target* coordinate) → **domain mutation** (`changeCode`) → **persist**. The DB gate catches a *different*
room already occupying the target coordinate; idempotency prevents a false-positive self-collision.

### 3. CQS DTO split — kill the generic "Response"
Drop the catch-all `RoomResponse`. The write side returns lightweight `Result` types carrying only the
fields directly affected by the command (id, changed field, timestamps). The read side returns rich
`View` types that may aggregate/flatten freely for the consumer without coupling to the write flow
(CQRS bypass — no domain reconstruction). Query ports return `Optional<RoomDetailView>` /
`Optional<RoomSummaryView>` directly.

### 4. Hybrid CQS packaging
- **Command side (1-1):** the `Result` is a `public static record Result(...)` nested *inside* its
  Command. No standalone `*Result.java`. Signature: `record RenameRoomCommand(...) implements
  Command<RenameRoomCommand.Result>`. Call sites use the explicit nested type
  (`RenameRoomCommand.Result result = commandBus.execute(command);`).
- **Query side (multi-1, global):** `View` types live in the sub-package `port.in.query.view`
  (`RoomDetailView`, `RoomSummaryView`). The Query *records* (`GetRoomByIdQuery`, `GetRoomByNameQuery`)
  stay in `port.in.query`, independent and type-safe — because a single View can serve many queries and
  must evolve out-of-phase.

### 5. Driving HTTP adapter — split, explicit, scoped
- `RoomCommandController` (injects **only** `CommandBus`; `POST` create, `PUT /{id}/rename`) and
  `RoomQueryController` (injects **only** `QueryBus`; `GET /{id}`, `GET /by-name/{name}`). No controller
  mixes the two buses.
- Command/Query objects are built as an explicit `var command = new XCommand(...)` **before**
  `bus.execute(...)` (trivial to breakpoint/debug; never `new` inside the `execute(...)` argument).
- Room-specific errors are centralized in `RoomExceptionAdvice`
  (`@RestControllerAdvice(assignableTypes = {RoomCommandController.class, RoomQueryController.class})`):
  `RoomNotFoundException`→404, `DuplicateRoomException`→409, `RoomDomainException`→400. The advice lives
  **inside the Room module** and is intentionally *not* promoted to the Shared Kernel, preserving module
  encapsulation (Spring Modulith boundary).

## Consequences

### Positive
- **Reference module:** Room now demonstrates the full golden recipe — one-way VOs, sealed domain events,
  CQS split, hybrid packaging, split driving controllers, scoped advice. Other modules can copy it.
- **Type safety end-to-end:** `Command<RenameRoomCommand.Result>`, `Query<RoomDetailView>`; the bus
  resolves handlers by exact generic type via `ResolvableType`.
- **Locality (Command) + evolvability (Query):** nested `Result` keeps a command's contract in one file;
  the `view` package lets read projections grow independently of writes.
- **Encapsulation:** business exceptions never leak into Shared Kernel; cross-module safety preserved.

### Negative / Trade-offs
- Two read projections (`Detail`/`Summary`) must be kept consistent with the schema (accepted CQRS cost,
  already noted in ADR 0002).
- Nested `Result` makes the Command file slightly larger; judged worth the locality gain.
- A deactivated room cannot be renamed — a deliberate freeze, may surprise callers (documented in API 409).

## Notes
- **Deferred (future branch):** Relocation (`room.relocateTo` → `RoomRenamedEvent(LOCATION_CHANGED)`) and
  actual publishing of `RoomRenamedEvent` to an Event Bus + the Workshop-module reaction described in
  `docs/architecture/diagrams/room-workshop-event-reaction.mermaid`. The event is designed and recorded
  now; dispatch is a later slice (Workshop module is still pending, progress-log entry 007).
- The rename flow's DB gate reuses the existing composite-uniqueness column
  (`(building, floor, code)`), so no schema migration was needed for this use case.
