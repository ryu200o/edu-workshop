# AGENTS.md — Agent System Instructions

This file defines the behavioral rules and architecture conventions that AI agents (and contributors)
MUST follow when working in this repository. Read it before making changes.

## Project Architecture

- **Top-level style:** Modular Monolith powered by **Spring Modulith**, which manages the module
  boundaries and enforces the *allowed dependencies* between modules (the outer ring).
- **Inner style per module:** **Hexagonal Architecture** (Ports & Adapters) combined with
  **DDD Tactical** patterns inside the `internal/` area.

### Conceptual layering

- `contract/` — public contracts shared *across* modules (DTOs, integration events).
- `internal/` — the encapsulated core of a module (domain, application, adapters). Information hiding
  boundary: by default everything here is **package-private**.
- Cross-module request handling lives in `internal/adapter/driving/module_api/` and MUST be named
  using an **underscore** (`module_api`), never a hyphen.

## Branch Strategy

- NO large/long-lived feature branches. Apply **Short-lived branches**.
- Slice features finely following the order: **Domain -> DB Adapter -> Expose API**.
- Open **small, continuous PRs** into `main`; merge frequently.

## Package Rules

- The `internal/` zone is an information-hiding boundary. Default visibility is **package-private**;
  only explicitly intended types are `public`.
- The cross-module incoming-request layer MUST be named **`module_api`** (underscore, not hyphen).
- Module API communication interface is exposed as `[ModuleName]ExposeAPI` (public, at module root);
  its implementation `[ModuleName]ExposeAPIImpl` lives in `internal/adapter/driving/module_api/`
  and stays package-private.
- **Exception layering:** a domain aggregate raises only **invariant violations** (e.g.
  `RoomDomainException`, `DuplicateRoomCodeException`, `IllegalRoomStateException`) — these stay in
  `internal/domain/model/exception/`. A **failed lookup / not-found** is an *application* concern, not a
  domain invariant, so it lives in `internal/application/exception/` and extends the shared base
  `shared.application.exception.ResourceNotFoundException` (abstract; `(resourceType, field, value)`
  constructor). Handlers throw the not-found exception after an empty port lookup; the domain never imports
  it. New modules (Workshop, …) follow the same split.

## Outbound Port Naming & DI Convention

- Read/write outbound ports are symmetric: **`RoomRepository`** (write: `loadById` / `save` only) and
  **`RoomReader`** (read, CQRS bypass). (Formerly `RoomQueryPort` — renamed; the CQS `Query`/`QueryBus`/
  `QueryHandler` message concepts are NOT affected.)
- DI field names are **non-abbreviated and type-derived** — always `RoomRepository roomRepository` and
  `RoomReader roomReader`. Never abbreviate to `repository` / `reader`.
- Implementations: `JpaRoomWriteAdapter` (impl `RoomRepository`, persistence/jpa) and `JooqRoomReadAdapter`
  (impl `RoomReader`, persistence/jooq) — adapter class names are intentional and kept as-is.
- **Uniqueness is NOT a repository concern.** `existsByCoordinate` / `existsByName` were removed from
  `RoomRepository`. The global-uniqueness arbiter is `RoomUniquenessPolicy` (domain interface, see below),
  whose IO impl `JpaRoomUniquenessPolicy` lives in the driven adapter.

## Modules & Skeleton

- Generate new modules with `bash create-module.sh <module-name>`. It produces the Spring Modulith +
  Hexagonal skeleton and the required `ExposeAPI` / `ExposeAPIImpl` pair.
- Application layer (per module) follows the golden structure: `application/port/in/{command,query}`,
  `application/port/out`, flat `application/handler`, `application/event`, `application/mapper`.

## Documentation Index (project "constitution")

Consult these before designing or coding. They are the source of truth:

- `docs/architecture/development-guidelines.md` — golden Application structure + Command/Query bus reference code.
- `docs/architecture/adr/0001-isolate-room-static-and-temporal-states.md` — Room static vs temporal state isolation.
- `docs/architecture/adr/0002-application-layer-restructuring-and-cqs-bypass.md` — Application layout,
  reject `domain/spi`, package-private handlers, CQRS bypass, per-module Command/Query bus.
- `docs/architecture/adr/0005-domain-policy-and-global-invariant.md` — **Accepted**: the global room-uniqueness
  invariant is a **Domain Policy** (`RoomUniquenessPolicy`, in `domain/model/policy/`). The aggregate receives the
  policy as an argument on every uniqueness-sensitive operation and throws `DuplicateRoomCodeException` /
  `DuplicateRoomNameException` itself.
  The Factory framing was dropped, but the policy is the current standard — follow this ADR.
- `docs/architecture/adr/0006-shared-command-query-bus.md` — Shared Command/Query Bus (supersedes ADR 0002 §5):
  shared kernel owns the bus; modules own Commands/Queries/Handlers.
- `docs/architecture/adr/0007-cross-module-data-decoupling-via-selective-snapshotting.md` — **Proposed**:
  Workshop decouples from Room via logical `room_id` UUID + selective `room_name_snapshot` /
  `room_location_snapshot` columns (no physical FK / cross-module JOIN); proactive sync via `RoomExposeAPI`,
  reactive sync deferred until Room events are published.
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

## Domain Rule: Global / Set-based Invariant (Uniqueness) — Domain-owned Policy

- A **set-based invariant** (e.g. no two rooms share the same `(building, floor, code)` coordinate, and no two
  share the same `(building, floor, name)`) cannot be proven *by* a single Aggregate — it needs an arbiter that
  looks at the whole set. But the **decision** of whether the invariant holds, and the exception it raises, belong
  to the **Domain** (Ubiquitous Language: `DuplicateRoomCodeException` / `DuplicateRoomNameException` are
  domain vocabulary).
- Mechanism (**ADR 0005, Accepted**): a domain-owned `RoomUniquenessPolicy` interface in `domain/model/policy/`
  answers the two uniqueness questions (`isCodeUnique`, `isNameUnique`). The **aggregate** receives the policy as
  an argument on every uniqueness-sensitive operation (`Room.create`, `changeCode`, `changeName`, `relocateTo`),
  checks it (after the idempotency skip, to avoid false-positive self-collision), and throws
  `DuplicateRoomCodeException` / `DuplicateRoomNameException` (each carrying only its own collision data).
  There is **no** `RoomFactory` — the aggregate is the single
  decision-maker.
- The Domain depends ONLY on the **domain interface** → it is pure at compile time (no infrastructure, no Spring).
  The IO that answers the policy lives in `JpaRoomUniquenessPolicy` (driven adapter), which depends on
  `RoomJpaRepository`. This is Hexagonal: the Domain knows a business rule, not a database.
- The **DB unique constraints** (`uk_rooms_building_floor_code`, `uk_rooms_building_floor_name`) remain the
  authoritative, race-proof gate. `JpaRoomWriteAdapter.save()` translates `DataIntegrityViolationException` →
  `DuplicateRoomCodeException` / `DuplicateRoomNameException` (correct type per constraint), so the TOCTOU
  race is still caught. The policy read and the DB
  constraint are **complementary**, not substitutive.
- **Guardrails:** never inject a Repository / outbound port directly into a domain object — always via the domain
  `RoomUniquenessPolicy` interface. The policy answers **only** the two uniqueness questions; do not turn it into a
  catch-all validator.
- **Handler role (thin):** load aggregate → build VOs → delegate to the aggregate passing the policy → save.
  Handlers never call `exists*` and never evaluate the invariant themselves. Asserting *invariant ordering* is a
  Domain concern, so it is tested in `RoomTest` (with an `ALWAYS_UNIQUE` stub) — NOT by spying on the policy inside
  handler tests.
- **Reconstitution** (`Room.reconstruct`) cleanly bypasses any uniqueness check (no spurious re-check on read).
