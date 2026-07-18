# ADR 0002: Application Layer Restructuring & CQS Bypass

- **Status:** Accepted
- **Date:** 2026-07-12
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** `docs/architecture/development-guidelines.md`, ADR 0001, ADR 0005, `.AGENTS.md`

## Context

The initial per-module skeleton placed the Application layer as `application/{command, query, port/in,
port/out, event}` and there was a temptation to host outbound ports under a `domain/spi` location.
As we prepare to actually implement the Application layer (Command/Query handlers, buses), we need a
single, unambiguous "golden" structure that all modules follow, aligned with the reference in
`development-guidelines.md`, and compatible with Spring Modulith's boundary enforcement.

## Decision

### 1. Golden Application structure (per module)
```
application/
├── port/
│   ├── in/
│   │   ├── command/   (write DTOs as records + CommandBus interface)
│   │   └── query/     (read DTOs/projections as records + QueryBus interface)
│   └── out/           (ALL outbound ports / SPI owned by the module)
├── handler/           (fully flattened — no sub-packages)
├── event/             (application-level events)
└── mapper/            (DTO <-> Domain converters, when needed)
```

### 2. Reject `domain/spi`; outbound ports live in `application/port/out/`
Outbound ports (SPI) are an Application concern (they express what the use cases need from the
outside world), not a Domain concern. We **reject any `domain/spi` package** and place **all**
outbound ports in `application/port/out/`. This keeps the Domain core pure (no port dependencies),
consistent with ADR 0001 and the "clean domain" outcome of the Room rework.

### 3. Flatten `handler/` with package-private visibility
All command/query handlers and the bus implementations are placed **flat** in `application/handler/`
with **no sub-packages**, and are declared **package-private** (no `public`). Other modules therefore
cannot import or interfere with them — the only exposed surfaces are the `CommandBus`/`QueryBus`
interfaces in `port/in/` and the module's `ExposeAPI`.

### 4. CQRS Bypass for the read side
Queries do **not** pass through the Domain Model. A query handler calls an outbound **query gateway**
that returns a response/projection DTO directly (read-optimized, `@Transactional(readOnly = true)`),
bypassing aggregate reconstruction. Commands keep going through the Domain Model (rich behavior +
invariants). Read and write outbound ports are separated (e.g. `RoomRepository` vs
`RoomReader`), enabling future read/replica splitting without touching the Application layer.

> **Update (Room module):** the read side is implemented with **JOOQ** (`JooqRoomReadAdapter`), querying the
> `rooms` table directly via generated type-safe table classes and mapping flat columns into `Room*View`
> projections — no JPA entity, no domain reconstruction. The write side stays on **JPA**
> (`JpaRoomWriteAdapter`); both share one datasource (logical C/Q split). See `development-guidelines.md` §3.6.

### 5. "Intentional duplication" — each module owns its own Command/Query Bus
> **Superseded by ADR 0006.** As of ADR 0006, the bus capability (interface, dispatcher, immutable
> registry, behavior-chain pipeline) moves into the Shared Application Kernel; modules keep only
> Commands/Queries/Handlers. The boundary-protection intent of this section is now achieved via
> pluggable `CommandBehavior` extension points rather than duplicated bus classes. The text below is
> retained for historical context.
Rather than sharing a single global bus, **each module declares its own `CommandBus`/`QueryBus`**
(and the `Command`/`Query`/`*Handler` shared framework interfaces per module). This intentional
duplication protects Spring Modulith boundaries: no module reaches across into another module's
application internals, and the buses resolve handlers only within the owning module's context.

## Consequences

### Positive
- One canonical structure across modules; `create-module.sh` scaffolds it automatically.
- Pure Domain (no SPI leakage), hidden handlers (package-private), fast reads (CQS bypass).
- Strong module isolation for Spring Modulith via per-module buses.

### Negative / Trade-offs
- Some boilerplate is duplicated per module (buses + framework interfaces) — accepted deliberately.
- Read and write models can diverge; developers must keep query projections in sync with schema.

## Notes
This branch only restructures packaging and documentation — **no business logic** is added. Handlers,
buses, DTOs and gateways will be implemented in the following feature branch.

## Refinement Note (ADR 0005)

ADR 0005 (Domain Factory Gateway & Global Invariant via Domain Policy Interface) refines the layering decided
here. It does **not** overturn §2 (reject `domain/spi`): outbound SPI ports still live in `application/port/out/`.
ADR 0005 introduces a *domain-owned Policy interface* (`RoomUniquenessPolicy`) for set-based invariants — a
distinct concept from an outbound SPI port — plus a Domain `RoomFactory` construction gateway. The application
`RoomExistencePort` (an SPI port that merely wrapped the uniqueness query) is retired in favor of the domain
policy. See ADR 0005 for the full rationale.
