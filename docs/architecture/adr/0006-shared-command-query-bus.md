# ADR 0006: Shared Application Kernel for Command/Query Bus

- **Status:** Proposed (under review — NOT yet accepted; ADR 0002 §5 remains the official decision until this is accepted and §5 is superseded)
- **Date:** 2026-07-18
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0002 (§5 intentional duplication), ADR 0001, `docs/architecture/development-guidelines.md`, `.AGENTS.md`, PR #17

---

## Context

ADR 0002 §5 currently mandates that **each module owns its own `CommandBus`/`QueryBus`** (and their
implementations, e.g. `room.internal.application.handler.SimpleCommandBus` / `SimpleQueryBus`). The shared
kernel (`io.github.ryu200o.eduworkshop.shared.cqs`) today holds only the contracts: `Command`, `Query`,
`CommandHandler`, `QueryHandler`. The bus interfaces and their implementations live **inside** each module.

During a recent architecture review, two alternative proposals (global `SpringCommandBus` /
`GlobalCommandBus` in the shared kernel) were raised. They argue that the bus is a stable, framework-level
concern with no business knowledge, and that the per-module duplication (~10 lines of `ResolvableType`
dispatch each) is unnecessary boilerplate once a second module exists.

This ADR does **not** change any code yet. It records the trade-off honestly, proposes a concrete
"Shared Application Kernel" architecture, and asks the Lead to decide whether to **supersede ADR 0002 §5**.
No implementation happens until this ADR is accepted.

---

## Problem Statement

1. Is the per-module bus (ADR 0002 §5) the right long-term home for dispatch logic, or should the bus
   become a shared, framework-ized capability?
2. If we centralize the bus, how do we preserve each module's ability to attach its **own pipeline /
   policy** (transaction, validation, authorization, logging, metrics, tracing) without forking the bus?
3. How do we avoid the known pitfalls of a global dispatcher (duplicate-handler startup failure,
   cross-module resolution ambiguity) while still reducing duplication?

---

## Current Architecture (as-is, not distorted)

```
shared.cqs/                         (shared kernel)
├── Command.java                    (marker interface, <R>)
├── Query.java                      (marker interface, <R>)
├── CommandHandler<C extends Command<R>, R>
└── QueryHandler<Q extends Query<R>, R>

room.internal.application.*
├── port/in/command/
│   ├── CommandBus.java             (interface, per-module)
│   └── CreateRoomCommand.java  ... (Command records)
├── port/in/query/
│   ├── QueryBus.java               (interface, per-module)
│   └── GetRoomByIdQuery.java   ... (Query records)
└── handler/
    ├── SimpleCommandBus.java       (impl, @Component, package-private)
    ├── SimpleQueryBus.java         (impl, @Component, package-private)
    ├── CreateRoomCommandHandler.java
    └── ... (handlers, package-private @Component)
```

- Each module **declares its own** `CommandBus`/`QueryBus` interface + `Simple*Bus` implementation.
- Each `Simple*Bus` resolves its handler via `ResolvableType.forClassWithGenerics(CommandHandler.class,
  command.getClass(), Object.class)` against the shared `ApplicationContext`, then `getBean`.
- Handlers are package-private `@Component`; only the bus interface + `ExposeAPI` are exposed.
- This is **intentional duplication** per ADR 0002 §5.

---

## Proposed Architecture (if accepted)

A **Shared Application Kernel** owns the bus *capability*; modules own only Commands, Queries, Handlers,
and (optionally) their own Policies.

```
shared.kernel.application.bus/                (shared kernel — new package)
├── CommandBus.java                           (interface, shared)
├── QueryBus.java                             (interface, shared)
├── CommandDispatcher.java                    (orchestration ONLY — Coordinator)
├── QueryDispatcher.java
├── HandlerResolver.java                      (CommandType -> Handler; no pipeline knowledge)
├── HandlerRegistry.java                      (immutable after startup; auto-discovery + validation)
├── CommandBehavior.java                      (Chain of Responsibility unit)
├── CommandPipeline.java                      (ordered chain of CommandBehavior + handler)
├── CommandPolicyResolver.java                (matcher-based; NO package-name identity)
└── BusConfiguration.java                     (@Configuration declaring the shared beans)

room.internal.application.*
├── port/in/command/
│   └── CreateRoomCommand.java ...            (Command records; NO bus interface here)
├── port/in/query/
│   └── GetRoomByIdQuery.java ...             (Query records)
└── handler/
    ├── CreateRoomCommandHandler.java
    └── ... (handlers, package-private @Component)
```

### Component responsibilities (strict SRP)

```
CommandBus
    │
    ▼
CommandDispatcher        (orchestration only — does NOT know Spring or pipeline internals)
    │
    ├───────────────┐
    ▼               ▼
HandlerResolver   CommandPipeline
    │               │
    └───────┬───────┘
            ▼
       CommandHandler
```

- **CommandBus** — public entry point (`execute`).
- **CommandDispatcher** — orchestrates: ask `HandlerResolver` for the handler, ask `CommandPipeline` to run
  the behavior chain that ends in the handler. Knows neither Spring nor behavior details.
- **HandlerResolver** — maps `Command type -> Handler` only. No pipeline, no policy knowledge.
- **CommandPipeline** — a Chain of Responsibility of `CommandBehavior` units terminating in the handler.
  Knows the behavior chain, not Spring.
- **CommandBehavior** — one cross-cutting concern (logging / validation / authorization / metrics /
  transaction). Chain link: `Object handle(Command<?> cmd, BehaviorChain next)`.
- **CommandPolicyResolver** — selects/orders the behavior chain for a command via a **matcher**
  (`Predicate<Class<?>>`), never by package name.
- **HandlerRegistry** — scans `CommandHandler<?>` beans at startup, validates, then **freezes immutable**
  (read-only at runtime; no re-scan, no re-compute, no synchronization).

### Roles (strictly separated)

**1. Bus (Coordinator only)**
- Resolves the handler for a given Command/Query type.
- Dispatches through the pipeline.
- Contains **NO business logic**. Knows only `Command`/`Query` → `Handler`.

**2. Behavior Chain / Policies (Extension Points, NOT the Bus)**
- Cross-cutting concerns are `CommandBehavior` links in a Chain of Responsibility:
  `LoggingBehavior → ValidationBehavior → AuthorizationBehavior → MetricsBehavior →
  TransactionBehavior → Handler`.
- Modules compose their own chain via `CommandPolicyResolver` (matcher-based registration). Room and
  Workshop can have different chains (e.g. Workshop adds `AuthorizationBehavior`) **without the bus or
  dispatcher changing**.
- This is the Open/Closed guarantee: new concern = new `CommandBehavior`, never an edit to
  `CommandDispatcher`.

**3. Handler**
- The module's use case. Lives inside the module, package-private. Contains the actual business behavior.

### Can we keep a Shared Bus but let each module have its own pipeline / policy?

**Yes, and without package-name identity.** Mechanism at architecture level (no code yet):

- `CommandDispatcher` delegates to `CommandPipeline` (a `CommandBehavior` chain) rather than calling the
  handler directly.
- `CommandPolicyResolver` exposes `Optional<CommandPipeline> resolve(Command command)` and is implemented
  by matcher-based registration, e.g. `record ModuleRegistration(Predicate<Class<?>> matcher,
  CommandPipeline pipeline)`. A command is matched by its type, not by its package string.
- Modules contribute `ModuleRegistration` beans; the shared bus composes them. No module forks the bus; it
  only supplies beans. The dispatcher and resolver never hard-code a module name.
- This keeps the Bus a pure Coordinator while preserving per-module customization — the flexibility ADR 0002
  §5 wanted, achieved via extension points instead of duplicated bus classes.

---

## Decision Drivers

- **Consistency** with ADR 0002 intent (protect module boundaries; no cross-module reach into internals).
- **DRY** where it costs nothing (dispatch logic is identical across modules).
- **Stability of the bus API** — `bus.execute(cmd)` has been stable across Axon/MediatR/Spring for ~20 years.
- **Extensibility (Open/Closed)** — modules must add cross-cutting concerns (logging, metrics, authorization,
  transaction strategy) as new behaviors/policies **without modifying the dispatcher**.
- **Low magic** — prefer Spring-native mechanisms (`ResolvableType`, `BeanFactory`, `ApplicationContext`)
  over manual reflection or forced boilerplate like `commandType()`.
- **Startup safety** — duplicate-handler collisions and missing-handler references must fail fast with
  dedicated, debuggable exceptions, not silently or via generic `IllegalStateException`.

### Why now?
The bus duplication is currently only ~10 lines × 1 module (Room). The trigger for revisiting ADR 0002 §5 is
the **anticipated arrival of further modules** (Workshop, Registration). The maintenance cost of N duplicated
bus classes, and the need for a **shared extension point** (so each module can attach its own pipeline without
forking the bus), is what makes this worth deciding now — before the second module is written, while the
refactor cost is still low. If the project were to stay a single-module system, the status quo would remain
preferable.

## Non-goals

This ADR deliberately does **NOT** propose:
- CQRS event-sourcing or full CQRS physical split (read/write stay logical-split, one datasource).
- A distributed / remote / async bus (dispatch is in-process only).
- Adopting Axon, MediatR, or any external CQRS framework.
- Saga / process-manager orchestration.
- Putting business logic inside the bus (the bus is a Coordinator only).
- Using package name as module identity for policy resolution (see Alternatives / Proposed).

## Success Criteria

This ADR is successful only if ALL hold after implementation:
- [ ] No duplicated per-module `Simple*Bus` classes remain.
- [ ] Modules depend only on the shared `CommandBus`/`QueryBus` interface, never on the dispatcher impl.
- [ ] Existing handlers compile and run **unchanged** (no new method such as `commandType()` required).
- [ ] A cross-cutting concern (logging/metrics/authorization/transaction) can be added as a new
      `CommandBehavior` **without editing `CommandDispatcher`**.
- [ ] Module compile-time / Spring Modulith dependency graph is unchanged (no new module edges).
- [ ] `ArchitectureTest` (Spring Modulith boundary check) stays green.
- [ ] Startup fails fast with `DuplicateCommandHandlerException` / `MissingCommandHandlerException` on
      misconfiguration, with a clear message naming the offending command type.

---

## Trade-offs

### Per-module Bus (status quo, ADR 0002 §5)

**Advantages**
- Strongest module isolation; each module fully owns its dispatch pipeline and can diverge freely.
- No shared-kernel coupling for application infrastructure; shared kernel stays minimal (contracts only).
- Trivial to reason about: "this bus only sees this module's handlers" (intent, even if the impl still
  queries the global context).
- Zero risk of cross-module handler collision at the architectural level.

**Disadvantages**
- Boilerplate duplicated per module (~10 lines of dispatch each). Accepted deliberately today.
- Dispatch behavior (e.g. error message, future middleware) must be changed in N places.
- As modules multiply, the "intentional duplication" becomes a maintenance tax.

### Shared Bus (proposed, ADR 0006)

**Advantages**
- Single source of truth for dispatch; DRY; one place to add future infrastructure (metrics, tracing hook).
- Shared kernel already hosts `Command`/`Query`/`*Handler` contracts, so the bus interface is a natural fit.
- Modules shrink: they declare only Command/Query/Handler; no bus class to write.
- Compile-time module dependency graph is unchanged (bus depends only on `CommandHandler<?,?>`, not on
  `room`/`workshop`), so Spring Modulith boundary checks still pass.

**Disadvantages**
- Supersedes ADR 0002 §5 — a deliberate architecture change requiring Lead sign-off.
- Global dispatcher scans the whole `ApplicationContext`; a duplicate handler (or two modules defining the
  same Command type) causes a startup failure unless explicitly guarded.
- The shared kernel becomes heavier (now owns application infrastructure, not just contracts).
- Risk of "shared-kernel creep": easy to gradually push more module-specific logic into the shared bus.

---

## Consequences

### If Accepted (supersedes ADR 0002 §5)
- ADR 0002 §5 is marked *Superseded by ADR 0006*.
- `CommandBus`/`QueryBus` interfaces + `Simple*Bus` impls move from each module into the shared kernel.
- Modules keep Commands/Queries/Handlers only.
- A `CommandPipeline`/`QueryPipeline` + `DispatchPolicy` extension model is introduced so modules retain
  per-module customization.
- Duplicate-handler collision is detected at startup with a clear `IllegalStateException`.

### If Rejected (ADR 0002 §5 stands)
- Per-module bus remains; no code change to the bus.
- The `RoomId` VO and other module work proceed independently.

---

## Alternatives Considered

1. **(Gemini) Global bus + per-module thin wrapper (`RoomCommandBus` delegating to `GlobalCommandBus`).**
   Rejected in review: the wrapper adds no real isolation (still calls the global bean) and introduces a
   pointless `RoomCommand` marker interface. No boundary benefit over a plain global bus, with extra
   ceremony.

2. **(ChatGPT) Global `SpringCommandBus` with `commandType()` declared on every handler.**
   Rejected in review: `commandType()` is boilerplate on every handler; `List<CommandHandler<?,?>>` +
   `toMap` throws on duplicate keys at startup with no guard; still a global dispatcher. We prefer Spring's
   `ResolvableType` over manual `commandType()` when/if we implement.

3. **(This ADR) Shared bus as Coordinator + pluggable Behavior-Chain / Policy extension points.**
   Preferred if we move to shared: keeps the bus free of business logic, separates `HandlerResolver` from
   `CommandDispatcher` (SRP), models cross-cutting concerns as a `CommandBehavior` Chain of Responsibility,
   resolves policies by matcher (not package name), freezes an immutable `HandlerRegistry`, and fails fast
   with dedicated `DuplicateCommandHandlerException` / `MissingCommandHandlerException`.

4. **Status quo (per-module bus).**
   Valid and currently official. Lowest risk; only cost is duplication.

---

## Migration Strategy (only if Accepted)

- **Phase 1 — Infrastructure:** introduce shared `CommandBus`/`QueryBus` interfaces + `CommandDispatcher`/
  `QueryDispatcher` + `HandlerRegistry` (auto-discovery via `ResolvableType`) + `BusConfiguration` in the
  shared kernel. No module changes yet.
- **Phase 2 — Pipeline abstraction:** add `CommandBehavior` + `CommandPipeline` (Chain of Responsibility) +
  `CommandPolicyResolver` (matcher-based `ModuleRegistration`) + default pass-through pipeline.
  `CommandDispatcher` delegates to the pipeline; `HandlerRegistry` is built immutable at startup
  (scan → validate → freeze; read-only at runtime). Dedicated `DuplicateCommandHandlerException` /
  `MissingCommandHandlerException` on misconfiguration. No business logic in the bus.
- **Phase 3 — Migration per module:** Room first (remove `SimpleCommandBus`/`SimpleQueryBus` + bus
  interfaces from `room.internal...`); then Workshop; then Registration (as they are created).
- **Phase 4 — Cleanup:** delete the duplicated per-module bus classes after all modules migrated; update
  `development-guidelines.md` and ADR 0002 §5 (mark superseded).

Each phase is independently revertible: phases 1–2 add shared code without touching modules (safe to ship
even if migration is later cancelled); phases 3–4 are per-module and can be rolled back by restoring the
module's local bus.

---

## Final Recommendation

This ADR takes **no side**. It records that:

- ADR 0002 §5 (per-module bus) is the **current official decision** and remains so until this ADR is
  accepted.
- A shared bus is a **technically legitimate** alternative whose main risk (global dispatch, duplicate
  collision) is mitigable via a Coordinator-only bus + pluggable Pipeline/Policy + startup guard.
- The decision belongs to the Lead. If the Lead favors DRY and accepts the shared-kernel trade-off, accept
  this ADR and supersede ADR 0002 §5, then implement per the Migration Strategy. If the Lead prefers
  maximum isolation and minimal shared-kernel surface, reject this ADR and keep ADR 0002 §5.

**No code is changed by this ADR.** Implementation waits for the Lead's decision.

---

## Notes

- The `RoomId` Value Object work on branch `refactor/room-id-and-bus` is **independent** of this ADR and
  proceeds regardless of the outcome.
- Per the review discussion: an ADR is a record of a decision at a point in time and may be superseded by a
  newer ADR when trade-offs change — that is the normal process, not a violation of prior decisions.
