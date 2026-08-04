# ADR 0016: Method Naming Convention for Outbound Ports & Module Facades (ExposeAPI)

* **Status**: ACCEPTED
* **Date**: 2026-08-04
* **Deciders**: Lead Engineer, Solution Architect (SA)
* **Technical Domain**: Ports & Adapters (Hexagonal), CQRS Read/Write Sides, Module Facade (ADR 0010), Naming Conventions

---

## 1. Context & Problem Statement

EduWorkshop mixes **Write Side** (aggregate load + mutation) and **Read Side** (view/DTO projection) responsibilities across two kinds of interfaces:

1. **Outbound Ports** — the `internal/application/port/outbound` SPI contracts:
   - Write ports (`*Repository`): load aggregates for mutation (`RoomRepository`, `WorkshopRepository`, `RegistrationRepository`, `MaintenanceScheduleRepository`).
   - Read ports (`*Reader`): query read-only projections for Views/DTOs (`RoomReader`, `WorkshopReader`, `RegistrationReader`).
2. **Module Facade / ExposeAPI** (ADR 0010) — the public cross-module contract surface (`RoomExposeAPI`, `WorkshopExposeAPI`, `RegistrationExposeAPI`).

Over time, method prefixes drifted (`find*` appearing in write repositories, `check*` appearing in read-side ExposeAPI lookups). This blurs the boundary between the **State-Transforming layer (Write)** and the **Data-Querying layer (Read)**, making it hard to tell at a glance whether a call mutates state or only reads, and which port contract a given prefix belongs to.

### Why this needs to be documented

* **Boundary clarity**: a reader must never mutate; a writer must never serve projections. The prefix is the first-line signal.
* **Cross-module trust**: `*ExposeAPI` methods are invoked across module boundaries (FacilityOps → Room/Workshop/Registration). Consistent prefixes make the read-only guarantee of an ExposeAPI call self-evident.
* **Future-proofing**: new features choose a prefix by pattern, not by taste.

---

## 2. Decision Drivers

* **Consistency**: one naming matrix applies to outbound ports *and* module facades alike.
* **Read-only by construction**: a `find*`/`count*` method is side-effect free by convention; a `load*` method belongs only to write-side aggregate loading.
* **Boundary integrity**: the rule reinforces ADR 0010 (cross-module communication is Application-only) and ADR 0005 (aggregates stay IO-free).
* **Low ceremony**: the matrix is simple enough to be applied in code review without tooling.

---

## 3. Decision Outline

### 3.1 The Naming Matrix

| Layer / Interface | Prefix (Mandatory) | Purpose | Examples |
|---|---|---|---|
| **Write port** (`*Repository`) | `load*` | Load an Aggregate Root / Entity into RAM for business mutation | `loadById`, `loadByIdWithLock`, `loadByRoomId`, `loadOverlappingPlanned`, `loadPublishedOverlappingWithTimeWindow`, `loadByWorkshopAndUser` |
| **Read port** (`*Reader`) | `find*` | Query read-only data for Views / DTOs / Projections | `findById`, `findByName`, `findAll`, `findByRoomAndTimeOverlap` |
| **Module Facade** (`*ExposeAPI`) — read lookups | `find*` | Return a Contract / DTO / `Optional` to a caller module | `findForRegistration`, `findByRoomAndTimeOverlap`, `findPlanningPermission` |
| **Aggregation lookups** (both sides) | `count*` | Return a scalar count (neither mutating nor projecting a single row) | `countOverlapping`, `countActiveByWorkshopIds` |
| **Existence predicate** (Facade) | `exists*` | Return a boolean existence check | `existsById` |

### 3.2 Rule Statements

1. **Write-side outbound ports (`*Repository`) MUST use `load*`** for any method that materializes an aggregate/entity.
   - **Violation**: a `*Repository` method named `find*` or `get*`.
2. **Read-side outbound ports (`*Reader`) MUST use `find*`** for any lookup returning a projection / DTO / `Optional`.
   - **Violation**: a `*Reader` (or Query Port) method named `load*`.
3. **Module Facade (`*ExposeAPI`) read lookups MUST use `find*`** when they pull a data/permission contract back to the caller.
   - **Violation**: a read lookup named `check*`, `get*`, or `load*`.
4. **`count*` and `exists*` are reserved, explicitly-allowed prefixes** for aggregation and existence checks on either side; they are NOT `find*`/`load*` violations.

### 3.3 Scope & Non-Scope

* The matrix applies to **outbound ports** (`internal/application/port/outbound/*`) and **Module Facades** (`*ExposeAPI`, ADR 0010).
* It does **NOT** apply to Spring Data infrastructure interfaces (`*JpaRepository`). Those are adapter-layer Spring Data contracts whose derived-query prefixes (`find*`, `count*`, `exists*`) are dictated by Spring Data itself — `load*` is not a valid Spring Data derived-query prefix. They are hidden inside the module's `internal` boundary and are not part of the port API.
* It does **NOT** rename Command/Query Bus message types (ADR 0006) — only method names on ports and facades.

---

## 4. Consequences

### Positive

- **Read vs Write is decidable from the method name** everywhere it matters (ports and cross-module facades).
- **Cross-module callers** can rely on `find*` being side-effect free and `count*`/`exists*` being cheap aggregation/predicate reads.
- **Code review** becomes a mechanical scan: any `find*` in a `*Repository` or `load*` in a `*Reader`/`*ExposeAPI` is flagged automatically.

### Trade-offs / Costs

- **Churn**: existing methods had to be renamed (ports: `findByRoomId`→`loadByRoomId`, `findOverlapping`→`loadOverlapping`, `findOverlappingPlanned`→`loadOverlappingPlanned`; ExposeAPI: `checkPlanningPermission`→`findPlanningPermission`). One-time cost, now done.
- **Infraternal naming** in adapters: adapter implementations carry the port prefix while their underlying `*JpaRepository` keeps Spring Data prefixes — a deliberate asymmetry, documented above, not a bug.

---

## 5. Compliance Checklist

1. All outbound write ports (`*Repository`) expose only `load*`/`save*`/`saveAll`/`delete*`/`count*` method names.
2. All outbound read ports (`*Reader`) expose only `find*`/`count*` method names.
3. All `*ExposeAPI` interfaces expose only `find*`/`count*`/`exists*` for read lookups (no `check*`, `get*`, `load*`).
4. Spring Data `*JpaRepository` infrastructure interfaces are explicitly exempt and keep `find*` derived-query prefixes.
5. New features follow §3.1 — enforced during review.

---

## 6. Implementation Checklist

1. **This ADR** documents the matrix and the two decision batches below.
2. **Batch 1 — Outbound Ports** (commit `757feb9`): renamed `MaintenanceScheduleRepository.findByRoomId`→`loadByRoomId`, `MaintenanceScheduleRepository.findOverlapping`→`loadOverlapping`, `WorkshopRepository.findOverlappingPlanned`→`loadOverlappingPlanned`; ripple through `JpaMaintenanceScheduleWriteAdapter`, `JpaWorkshopWriteAdapter`, `ScheduleRoomMaintenanceCommandHandler`, `PlannedWorkshopKicker`, tests, and javadoc/ADR 0015 references.
3. **Batch 2 — Module Facade** (commit `986f2b6`): renamed `RoomExposeAPI.checkPlanningPermission`→`findPlanningPermission`; ripple through `RoomExposeAPIImpl`, `PlanWorkshopCommandHandler`, `PublishWorkshopCommandHandler`, `ChangeWorkshopRoomCommandHandler`, and their tests.
4. **Tests**: full suite green after both batches (366/366).

---

## 7. Glossary

- **Write Side**: the path that loads an aggregate and mutates its state; orchestrated by Command Handlers via `*Repository` write ports.
- **Read Side**: the path that projects data for queries; served via `*Reader` read ports and `*ExposeAPI` lookups (CQRS bypass, ADR 0002/0010).
- **Derived Query**: a Spring Data method whose query is derived from its name (`findByRoomId` → `SELECT ... WHERE room_id = ?`); only `find`/`read`/`get`/`query`/`stream`/`count`/`exists` are valid prefixes.

---

*Supersedes no prior ADR; complements ADR 0002 (CQRS bypass), ADR 0010 (cross-module Application-only dependencies & Module Facade), ADR 0005 (global rules orchestration).*
