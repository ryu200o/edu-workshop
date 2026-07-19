# ADR 0005: Global Uniqueness Invariant via a Domain-Owned `RoomUniquenessPolicy`

- **Status:** Accepted
- **Date:** 2026-07-19 (revised; supersedes the earlier factory-based framing of ADR 0005)
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0002, ADR 0003, ADR 0004, `.AGENTS.md`, `docs/db/database.md`

## Context

The **global room-uniqueness invariant** — *no two rooms may occupy the same `(building, floor, code)`
coordinate* **and** *no two rooms may share the same `(building, floor, name)` at one location* — is a
**set-based invariant**. By Evans/Vernon, an Aggregate can only guarantee invariants within *its own*
consistency boundary; a rule that depends on the *set of all rooms* needs an arbiter that looks down from
above. An Aggregate therefore **cannot self-prove its own uniqueness**; if it called a repository directly
we would commit the "aggregate calls repository" anti-pattern and pollute the Domain with IO.

This invariant had leaked as a repeated naked guard in the application handlers:

- `CreateRoomCommandHandler`: `if (roomRepository.existsByCoordinate(...))` then `if (roomRepository.existsByName(...))`
- `RenameRoomCommandHandler`: `if (roomRepository.existsByName(...))`
- `RelocateRoomCommandHandler`: `if (roomRepository.existsByCoordinate(...))` then `if (roomRepository.existsByName(...))`
- `ChangeRoomCodeCommandHandler`: `if (roomRepository.existsByCoordinate(...))`

Business knowledge lived in orchestration, quadruplicated; and `DuplicateRoomException` is already Domain
vocabulary (`domain.model.exception`) yet was thrown from the Application — semantically incoherent. The
uniqueness check was also expressed as `exists*` methods on the **outbound repository port**
(`RoomRepository`), mixing a global-invariant concern with persistence.

### Earlier framing (rejected in this revision)

An earlier version of this ADR introduced a `RoomFactory` (a Spring bean) as the single construction
gateway that depended on the policy. On reflection, the Factory added an indirection layer that owns
*no* business logic of its own — construction logic already lives on the aggregate (`Room.create`). We
therefore drop the Factory and keep only the **Domain Policy interface**: the aggregate itself becomes
the single decision-maker, receiving the policy as an argument on each uniqueness-sensitive operation.
This keeps the aggregate Rich (construction *and* mutation enforce the invariant) without a redundant
gateway object.

### Clarification vs ADR 0002 (no contradiction)

ADR 0002 §2 rejects `domain/spi` and places outbound **SPI ports** in `application/port/out/` to keep the
Domain pure. ADR 0005 does **not** overturn that. `RoomUniquenessPolicy` is a **Domain Policy interface** —
a domain-owned *business rule* — which is a *distinct concept* from an outbound SPI port (an infrastructure
need expressed by the app). The application `RoomRepository.exists*` methods (outbound port) are therefore
**retired** in favor of the domain-owned `RoomUniquenessPolicy`; uniqueness becomes a first-class domain
concept instead of a repository capability. See the refinement note appended to ADR 0002.

## Decision

### 1. Global invariant = a Domain Policy interface (not aggregate, not app SPI)
New `RoomUniquenessPolicy` in `domain/model/policy/`:
```java
public interface RoomUniquenessPolicy {
    /** True when NO other room occupies the coordinate (location + code). */
    boolean isCodeUnique(RoomLocation location, int code);

    /** True when NO other room occupies the (location + name) pair. */
    boolean isNameUnique(RoomLocation location, RoomName name);
}
```
The Domain owns the *specification* of the invariant. The IO implementation lives in the driven adapter
(`adapter/driven/persistence/jpa/JpaRoomUniquenessPolicy`), satisfying Hexagonal: the Domain depends only
on a domain interface and knows nothing about the database.

### 2. The aggregate is the single decision-maker
The policy is passed **into the aggregate** as an argument on every uniqueness-sensitive operation:

- `Room.create(name, location, code, capacity, RoomUniquenessPolicy policy)` — checks both `isCodeUnique`
  and `isNameUnique` before constructing; throws `DuplicateRoomException` (with the correct `Reason`) on
  violation.
- `Room.changeCode(int newCode, RoomUniquenessPolicy policy)` — checks `isCodeUnique` after the idempotency
  skip.
- `Room.relocateTo(RoomLocation newLocation, RoomUniquenessPolicy policy)` — checks both `isCodeUnique` and
  `isNameUnique` after the idempotency skip (relocation preserves code *and* name).
- `Room.changeName(String newName, RoomUniquenessPolicy policy)` — checks `isNameUnique` after the
  idempotency skip.

Idempotency (same code / same location / same name) is checked **before** the policy call, inside the
aggregate, to avoid a false-positive self-collision and needless IO. The `throw` semantics (domain
vocabulary + accurate `Reason`) live in the Domain — invariant and exception reunited under Domain
ownership.

### 3. Application layer becomes thin-and-correct
Handlers inject **only** the domain `RoomUniquenessPolicy` (in addition to `RoomRepository` for
load/save). They no longer call `exists*` on the repository and never wire `RoomName` for the guard:

- **Create:** `Room room = Room.create(name, location, code, capacity, policy); repo.save(room);`
- **ChangeCode / Rename / Relocate:** load aggregate → delegate to `room.changeCode(...)/changeName(...)/relocateTo(...)`
  passing `policy` → `save`. The handler never evaluates the invariant itself.

This retires the scattered `if (existsBy...)` in the four handlers into one domain-owned arbiter.

### 4. `RoomRepository` port is simplified
`existsByCoordinate` / `existsByName` are **removed** from `RoomRepository`. The port returns to its
primitive persistence contract (`save`, `loadById`). Uniqueness IO moves exclusively into
`JpaRoomUniquenessPolicy` (which depends on `RoomJpaRepository`).

### 5. DB unique constraint = authoritative race-proof gate
`uk_rooms_building_floor_code` and `uk_rooms_building_floor_name` remain the final integrity authority.
The Policy's read is a fast-fail / UX optimization; `JpaRoomWriteAdapter.save()` still translates
`DataIntegrityViolationException` → `DuplicateRoomException` (with the violated-constraint `Reason`), so
the TOCTOU race is still caught. The two mechanisms are **complementary, not substitutive**.

### 6. Dependency purity vs runtime IO (explicit)
The aggregate depends ONLY on a **Domain interface** → pure at compile time (Hexagonal: Domain knows no
infrastructure). The Policy's DB read is runtime IO executed in the **infrastructure** implementation, not
a layering violation. Trade-off accepted: every create/update triggers reads through the Policy impl
(latency cost), justified by centralized invariant enforcement and fail-fast UX.

## Consequences

### Positive
- **Single chokepoint** for uniqueness across create/rename/relocate/changeCode — a future module cannot
  "forget" the check.
- Domain owns the invariant contract + exception + `Reason` (Ubiquitous Language coherent).
- Application is thin: no `exists*` calls, no `RoomName` wiring for the guard, no naked `if`.
- Aggregate stays Rich (both construction and mutation enforce the invariant); no redundant Factory.
- Compile-time pure Domain (only domain interfaces); runtime IO correctly placed in infra.
- Reconstitution (`Room.reconstruct`) cleanly bypasses any uniqueness check (no spurious re-check on read).

### Negative / Trade-offs
- One (or two) extra reads per create/update (latency) — accepted.
- The aggregate now receives a domain interface argument on mutation methods — must stay disciplined:
  never inject a Repository / outbound port directly into a domain object; always via the domain Policy
  interface.
- Removing `exists*` from `RoomRepository` requires rewiring 4 handlers + adapter + tests (mechanical).
- Risk if misapplied: turning the policy into a catch-all validator — guardrail: the policy answers
  **only** the two uniqueness questions.

## Notes
- Supersedes the factory-based framing of ADR 0005; refines (does not overturn) ADR 0002 §2 — see the
  refinement note in ADR 0002.
- Fitness function (future ArchUnit): `domain/**/policy` must not depend on `application`, `port`, or
  Spring; no domain object calls a Repository directly.
- DB schema unchanged (no migration needed).
