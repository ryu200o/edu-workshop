# ADR 0002: Application Layer Restructuring & CQS Bypass

- **Status:** Accepted
- **Date:** 2026-07-12
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** `docs/architecture/development-guidelines.md`, ADR 0001, `.AGENTS.md`

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
`RoomQueryPort`), enabling future read/replica splitting without touching the Application layer.

### 5. "Intentional duplication" — each module owns its own Command/Query Bus
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
