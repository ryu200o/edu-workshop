# ADR 0005: Domain Factory Gateway & Global Invariant via Domain Policy Interface

- **Status:** Accepted
- **Date:** 2026-07-15
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0001, ADR 0002, ADR 0003, ADR 0004, `.AGENTS.md`, `docs/architecture/development-guidelines.md`,
  `docs/db/database.md`

## Context

The **global room-uniqueness invariant** — *no two rooms may occupy the same `(building, floor, code)`
coordinate* — is a **set-based invariant**. By Evans/Vernon, an Aggregate can only guarantee invariants
within *its own* consistency boundary; a rule that depends on the *set of all rooms* needs an arbiter that
looks down from above. An Aggregate therefore **cannot self-prove its own uniqueness**; if we made it call a
repository to check, we would commit the "aggregate calls repository" anti-pattern and pollute the Domain
with IO.

Historically this invariant leaked as a repeated naked `if (roomExistencePort.existsByBuildingAndFloorAndCode(...))
throw new DuplicateRoomException(...)` in `CreateRoomCommandHandler` (:42), `RenameRoomCommandHandler` (:50) and
`RelocateRoomCommandHandler` (:52). Business knowledge lived in orchestration, tripled; and `DuplicateRoomException`
is already Domain vocabulary (`domain.model.exception`) yet was thrown from the Application — semantically
incoherent.

An earlier proposal (Option A) fixed ownership by introducing a `RoomUniquenessPolicy` Domain interface that the
handlers called directly. That still left the guard call in the Application layer and left `Room.create` receiving
an *externally built* `RoomName` — so the name-derivation wiring (`RoomName.of(location, code)`) kept leaking into
the handler.

The locked decision (Solution 3, refined through the architecture survey) resolves both leaks:

- A **Domain Factory** is the **single construction gateway**. It depends ONLY on a **Domain Policy interface**
  (`RoomUniquenessPolicy`) — pure by *dependency* (Hexagonal satisfied). The Policy's IO implementation lives in the
  driven adapter (`application/port/out` / adapter). Construction logic (name derivation, local invariants, event)
  stays ON the aggregate via a package-private factory-method.
- **DB unique constraint** (`uk_rooms_building_floor_code`) remains the authoritative, race-proof gate.

### Clarification vs ADR 0002 (no contradiction)

ADR 0002 §2 rejects `domain/spi` and places outbound **SPI ports** in `application/port/out/` to keep the Domain
pure. ADR 0005 does **not** overturn that. It introduces a **Domain Policy interface** — a domain-owned business
rule — which is a *distinct concept* from an outbound SPI port (an infrastructure need expressed by the app). The
application `RoomExistencePort` (an SPI port that merely wrapped the uniqueness query) is therefore retired in
favor of the domain-owned `RoomUniquenessPolicy`; uniqueness becomes a first-class domain concept instead of an
application SPI. See the refinement note appended to ADR 0002.

## Decision

### 1. Global invariant = a Domain Policy interface (not aggregate, not app SPI)
New `RoomUniquenessPolicy` in `domain/model/policy/`:
```java
public interface RoomUniquenessPolicy {
    /** True when NO other room occupies the coordinate of {@code code} at {@code location}. */
    boolean isSatisfiedBy(RoomLocation location, String code);

    /** Domain guard: enforces the invariant, translating a violation into domain vocabulary. */
    default void ensureUniqueOrThrow(RoomLocation location, String code) {
        if (!isSatisfiedBy(location, code)) {
            throw new DuplicateRoomException(RoomName.of(location, code), location);
        }
    }
}
```
The Domain owns BOTH the specification AND the throw semantics. The adapter implements only the pure boolean; the
`throw` lives in the Domain (IO-free) — invariant + exception reunited under Domain ownership.

### 2. Domain Factory = single construction gateway
`RoomFactory` in `domain/model/entity/` (same package as `Room`):
- A Spring bean depending ONLY on `RoomUniquenessPolicy` (domain interface) — pure at compile time.
- `Room create(RoomLocation location, String code, int capacity)`:
  1. `policy.ensureUniqueOrThrow(location, code)` — fail-fast / UX read.
  2. delegate to `Room.create(location, code, capacity)` (package-private factory-method on the aggregate).
- Application handler injects ONLY `RoomFactory` + `RoomStateGateway` (never the Policy directly):
  `Room room = roomFactory.create(location, code, capacity);`

### 3. Aggregate construction logic stays ON the aggregate (Rich, not relocated)
- `Room.create(RoomLocation, String code, int capacity)` becomes **package-private**; it **self-derives**
  `RoomName.of(location, code)` internally and drops `requireNameConsistentWithLocation` (there is no external
  name left to distrust). Local invariants (positive capacity, non-null) are enforced here, atomically.
- `Room.reconstruct(...)` (existing) remains the **reconstitution** path — NO invariant check, NO event. DB load
  bypasses the Factory entirely, so no spurious uniqueness re-check is triggered on read.

### 4. Identity-changing mutations route through the SAME Policy
- `Room.changeCode(String newCode, RoomUniquenessPolicy policy)` and
  `Room.relocateTo(RoomLocation newLocation, RoomUniquenessPolicy policy)` call
  `policy.ensureUniqueOrThrow(...)` internally before mutating and emit `RoomRenamedEvent`. The aggregate stays the
  decision-maker (Rich); IO happens only through the injected domain interface.
- **Idempotency skip** (same code / same location) stays BEFORE the policy call to avoid false-positive
  self-collision and needless IO.
- This retires the scattered `if (existsBy...)` in the three handlers into one domain-owned arbiter.

### 5. Application layer becomes thin-and-correct
- **Create:** map primitives → `roomFactory.create(location, code, capacity)` → `roomStateGateway.save(room)`.
- **Rename/Relocate:** load aggregate → idempotency skip → `room.changeCode(newCode, policy)` /
  `room.relocateTo(newLocation, policy)` → `save`. The Policy travels as a domain argument; the handler never
  calls the Policy directly and never wires `RoomName`.
- `RoomExistencePort` (application `port.out`) is **DELETED** — its role is absorbed by the Domain
  `RoomUniquenessPolicy`. One concept, one port (resolves the "two ports, same purpose" ambiguity).

### 6. DB unique constraint = authoritative race-proof gate
`uk_rooms_building_floor_code` (and `uk_rooms_name`) remain the final integrity authority. The Factory's Policy
read is a fast-fail / UX optimization; the driven adapter translates `DataIntegrityViolationException` →
`DuplicateRoomException` so the TOCTOU race is still caught. The two mechanisms are **complementary, not
substitutive**.

### 7. Dependency purity vs runtime IO (explicit)
The Factory depends ONLY on a **Domain interface** → pure at compile time (Hexagonal: Domain knows no
infrastructure). The Policy's DB read is runtime IO executed in the **infrastructure** implementation, not a
layering violation. Trade-off accepted: every create/update triggers one read to the Policy impl (latency cost),
justified by centralized invariant enforcement and fail-fast UX.

## Consequences

### Positive
- **Single chokepoint** for uniqueness across create/rename/relocate — a future module cannot "forget" the check.
- Domain owns the invariant contract + exception (Ubiquitous Language coherent).
- Application is thin: no Policy mock, no `RoomName` wiring, no naked `if`.
- Aggregate stays Rich (construction + mutation logic on the aggregate; Factory is a thin gateway).
- Compile-time pure Domain (only domain interfaces); runtime IO correctly placed in infra.
- Reconstitution cleanly bypasses the Factory (no spurious checks).

### Negative / Trade-offs
- One extra read per create/update (latency) — accepted.
- Factory depends on a domain interface that implies IO at runtime — must stay disciplined: never inject a
  Repository / outbound port directly into a domain object; always via the domain Policy interface.
- `RoomExistencePort` removal requires rewiring 3 handlers + adapter + tests (mechanical).
- Risk if misapplied: turning the Factory into a God object that also absorbs mutations — guardrail: **Factory =
  creation only**; identity-changing mutations go through aggregate methods + the same Policy.

## Notes
- Supersedes the scattered `RoomExistencePort` pattern; refines (does not overturn) ADR 0002 §2 — see the
  refinement note in ADR 0002.
- Fitness function (future ArchUnit): `domain/**/factory` and `domain/**/policy` must not depend on `application`,
  `port`, or Spring; no domain object calls a Repository directly.
- DB schema unchanged (no migration needed).
