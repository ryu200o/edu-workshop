# ADR 0010: Cross-Module Dependencies Are Application-Only

- **Status:** Accepted (2026-07-22)
- **Date:** 2026-07-22
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0007 (Cross-Module Data Decoupling), ADR 0008 (Room Allocation Policy),
  `AGENTS.md`, `docs/architecture/development-guidelines.md`, PR #30

---

## Context

When implementing the Workshop `schedule()` use case, the team needed to fetch room data from the
Room module via `RoomExposeAPI` and use it to build a `RoomReference` in the Workshop domain.

An early design draft proposed:

```java
// In Workshop Domain model (RoomReference.java):
import room.contract.RoomSnapshot;

public static RoomReference from(RoomSnapshot snapshot) { ... }
```

This was rejected during review. The design violates a fundamental modular monolith constraint:

> **Public module API (`*ExposeAPI`, `contract/*`) being public does NOT mean every layer of another
> module can consume it.**

Cross-module access must respect **both** the Module Boundary **and** the Layer Boundary.

---

## Decision

### 1. Cross-module dependencies are Application-only

A module's Domain layer must NEVER import or reference:

- Another module's `*ExposeAPI` interface
- Another module's `contract/*` DTOs
- Another module's Application classes
- Another module's infrastructure classes

The **only** legal cross-module dependency is Application → `*ExposeAPI` → contract DTO, all
within the Application layer.

### 2. The flow for cross-module data access

```
Module A Application (handler)
        │
        ▼
Module B ExposeAPI          ← Application knows ExposeAPI
        │
        ▼
Contract DTO                 ← Application knows contract DTO
        │
        ▼
Module A Application
  maps Contract DTO → Module A Domain VO     ← mapping stays in Application
        │
        ▼
Module A Domain Aggregate     ← Domain knows only its own VOs, never contract DTOs
```

### 3. Mapping is Application responsibility

The Application handler is responsible for converting contract DTOs into Domain VOs.
The Domain never sees the DTO. This includes:

- Constructor calls: `new RoomReference(dto.roomId(), dto.name(), ...)`
- String formatting: `building + "/" + floor`
- Null/default handling
- Any other data transformation

#### The Application layer as Anti-Corruption Layer (ACL)

This mapping role is a textbook **Anti-Corruption Layer** (Evans, DDD). Each module is a
Bounded Context with its own Ubiquitous Language. When data crosses between contexts, the
Application layer translates so that each Domain stays pure and unaware of the other's model.

For example, the `RoomSnapshot → RoomReference` mapping:

```
Room's Ubiquitous Language:     building (String), floor (int)
Workshop's Ubiquitous Language: roomLocationSnapshot (String, display format)
```

The ACL translates between them. This prevents Room's structural changes (e.g. splitting
`building` into `campus` + `block`) from cascading into Workshop's Domain. The Application
handler is the single translation point — not the Domain VO and not the controller.

### 4. What the Domain MAY depend on

The Domain of a module may only work with:

- Primitives and JDK types (`String`, `UUID`, `Instant`, etc.)
- Its own Value Objects (e.g. `RoomReference`, `WorkshopId`, `RoomName`)
- Its own Aggregate(s) (e.g. `Workshop`, `Room`)
- Its own Domain Events and Domain Exceptions

### 5. Exceptions stay in the same module

Each module defines its own Application-layer exceptions.
A module must NOT throw or import another module's exception types.
Exceptions from another module (e.g. `RoomNotFoundException`) must be caught at the boundary
and translated if necessary, or simply not exposed across the boundary at all (use `Optional`
in the API instead).

---

## Consequences

### Positive (Pros)

- **Domain purity preserved.** The Domain stays framework-free, module-independent, and
  focused on business rules. No foreign DTOs leak into its model.
- **Contract changes are contained.** If Room renames a field in `RoomSnapshot`, only the
  Workshop Application handler needs updating — the Workshop Domain is unaffected.
- **Boundaries are clear.** Developers know exactly where to look for cross-module mapping:
  Application handlers, not Domain VOs.
- **Hexagonal consistency.** The Domain `internal/` information-hiding boundary now applies
  both within the module and to other modules. Other modules are "infrastructure" as far as
  this module's Domain is concerned.
- **Testability.** Domain tests never need to mock `ExposeAPI` or set up foreign DTOs.
  All cross-module concerns are tested at the handler (Application) level.

### Negative / Trade-offs (Cons)

- **More boilerplate in handlers.** Each cross-module call requires explicit mapping code
  in the handler. A VO factory could reduce this, but only if it stays in Application.
- **Stricter code review discipline.** Reviewers must flag any Domain-level import from
  another module. The principle must be enforced manually (no compiler check).

---

## Compliance Guide

### What to look for in code review

❌ **Illegal — Domain imports another module's contract:**

```java
// workshop/internal/domain/model/RoomReference.java
import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;

public static RoomReference from(RoomSnapshot snapshot) { ... }
```

✅ **Legal — Application maps the DTO:**

```java
// workshop/internal/application/handler/ScheduleWorkshopCommandHandler.java
import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;
import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;

RoomSnapshot snapshot = roomExposeApi.findRoomSnapshot(roomId)
    .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", roomId));

RoomReference roomRef = RoomReference.of(
    snapshot.roomId(),
    snapshot.name(),
    snapshot.location().building() + "/" + snapshot.location().floor()
);
workshop.schedule(roomRef, now);
```

❌ **Illegal — Domain throws or imports another module's exception:**

```java
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
throw new RoomNotFoundException("id", roomId);
```

✅ **Legal — Application defines its own exception:**

```java
// workshop/internal/application/exception/ReferencedRoomNotFoundException.java
public final class ReferencedRoomNotFoundException extends ResourceNotFoundException {
    public ReferencedRoomNotFoundException(String field, Object value) {
        super("ReferencedRoom", field, value);
    }
}
```

---

## Validation

- PR #30 (Workshop schedule use case) is the first implementation to follow this ADR.
- All future cross-module interactions (Workshop ↔ Room, Registration ↔ Workshop, etc.)
  must adhere to this principle.
- This ADR supersedes any earlier guidance implying that cross-module Domain access is acceptable.
