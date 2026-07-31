# ADR 0011: Spring Modulith Event Publication Registry as the Transactional Outbox

- **Status:** Accepted (2026-07-31)
- **Date:** 2026-07-31
- **Deciders:** Lead Engineer / Architecture Guild
- **Related:** ADR 0007 (Cross-Module Data Decoupling), ADR 0010 (Cross-Module Dependencies
  Are Application-Only), `docs/db/database.md`, PR (Phase 2 of the event-publishing plan)

---

## Context

The Workshop module decouples from Room via logical `room_id` + selective snapshot columns
(ADR 0007). Snapshots are refreshed proactively through `RoomExposeAPI` and reactively by
`WorkshopRoomEventHandler`, which listens for Room **integration events**
(`RoomRenamedIntegrationEvent`, ...).

The reactive path initially used the plain Spring `ApplicationEventPublisher`:
events are delivered synchronously in-process and are lost forever if the process crashes
between the business commit and the listener execution. For cross-module consistency we need
**at-least-once** delivery of integration events, i.e. a transactional outbox, without coupling
the modules to yet another infrastructure concern.

An earlier design draft considered a hand-rolled outbox table + poller. It was rejected: it
would duplicate retry/completion bookkeeping that Spring Modulith already provides, and would
introduce a bespoke concurrency surface to get right.

---

## Decision

### 1. Spring Modulith Event Publication Registry is the outbox

Enable the **Event Publication Registry** (`spring.modulith.events.publication-registry.enabled=true`,
runtime + test config). It persists one publication row **per (event, listener)** in the **same
database transaction** as the business write, and delivers each event asynchronously to its
listener after commit.

The outbox sits **below** ports & adapters: it is transparent to the Application layer. Handlers
keep calling the existing event publisher ports (`RoomDomainEventPublisher`, ...); persistence
and replay of publications are infrastructure concerns.

### 2. Persistence model

Managed by Flyway `V7__create_event_publication_table.sql` (authoritative; copied from the official
Modulith JDBC schema with two documented portability tweaks for H2/PostgreSQL):

| Concept | Mechanism |
| :--- | :--- |
| Row identity | `id` (UUID PK), one row per (event, listener) |
| Event | `event_type` (FQCN) + `serialized_event` (JSON) |
| Listener | `listener_id` (FQCN + method) |
| Delivery | `publication_date`; completion recorded by **UPDATE** `completion_date` |
| State | `status` (`COMPLETED` / `FAILED`), `completion_attempts`, `last_resubmission_date` |

Completion mode is **`update`** (the default): completed rows are kept, updated with
`completion_date`, so the registry remains the durable audit of what was delivered.

Indexes:
- `event_publication_by_completion_date_idx` — completion-date scans for retry scheduling.
- `event_publication_by_listener_id_serialized_event_idx` — the registry's
  BY_EVENT_AND_LISTENER_ID upsert lookup.

### 3. Delivery and failure semantics

- **At-least-once:** the row is durable before the listener runs. If the listener throws, the
  row stays uncompleted (`status = FAILED`, `completion_attempts = 1`) and is retried.
- **Replay on restart is OFF by default** (`spring.modulith.events.republish-outstanding-events-on-restart`
  is opt-in). The app is a single instance; re-delivery on every restart would duplicate
  idempotency work. Operators enable it deliberately when recovery is required.
- **Programmatic recovery:** `FailedEventPublications.resubmit(ResubmissionOptions.defaults())`
  re-delivers failed/incomplete publications through the `PersistentApplicationEventMulticaster`,
  which implements both `FailedEventPublications` and `IncompleteEventPublications`.

### 4. Both domain and integration events are durable

The registry persists **every** event/listener delivery in the same transaction. Domain events
are internal to their bounded context (handled by intra-module listeners); integration events
are the stable cross-module contracts (ADR 0010). Each receives its own publication row, so both
reactive snapshot sync and intra-module reactions survive a crash.

### 5. Known framework interaction (observability)

Modulith's observability interceptor formats intercepted method signatures. A method parameter of
generic type `Command<R>` normalizes to `Command<?>` whose unbounded wildcard resolves to `null`
and crashes the formatter. Because the `shared` module is OPEN (everything exposed → wrapped),
this would NPE on every command dispatch. `CommandDispatcher.dispatch` therefore declares its
parameter as `Object` and casts internally (see its Javadoc). Keep this in mind for any new
public method on a `shared` bean that references a generic type.

---

## Consequences

### Positive (Pros)

- **At-least-once cross-module delivery** with no hand-rolled outbox/poller code.
- **Strong consistency by construction:** publication insert and business write share one
  transaction — no dual-write window.
- **Infrastructure invisible to Application:** handlers keep using event publisher ports;
  no port/interface changes required by the outbox.
- **Operational tools for free:** completion status, retry attempts, failed-publication
  resubmission, and optional restart replay are provided by Modulith.

### Negative / Trade-offs (Cons)

- **One publication row per listener** (not per event): a single event with N listeners creates
  N rows — storage doubles accordingly.
- **Restart replay must stay opt-in** for single-instance deployments; enabling it requires
  idempotent listeners.
- **Completion-mode `update` keeps completed rows** forever unless pruned via
  `CompletedEventPublications.deletePublicationsOlderThan(...)`; pruning is a deliberate, later
  operational concern.
- **Observability formatter constraint** adds the `Object`-typed dispatcher signature (Section 5).

---

## Compliance Guide

- Do **not** disable the registry or switch the completion mode without an ADR.
- The `event_publication` table must remain owned by Flyway (`V7`); new Modulith columns are
  added via a new migration, diffed against the official schema.
- Handlers must keep publishing through the existing domain event publisher ports; the outbox is
  transparent and must not leak into Application code.
- When a new listener is added, expect a new publication row per event for that listener.
- Any new public method on a `shared`-module bean must avoid generic parameter types that could
  resolve to an unbounded wildcard (see Section 5).

---

## Validation

- `EventPublicationDurabilityTest` — domain + integration events recorded in the same TX as the
  business write, `serialized_event` carries the business payload, snapshot sync after rename,
  and rollback leaves no publication row.
- `EventPublicationFailureRecoveryTest` — failed listener leaves an uncompleted row; `resubmit`
  re-delivers and completes it.
- `EventPublicationRestartReplayTest` — restart replay (opt-in property) re-delivers outstanding
  publications.
- Full suite: 162 tests green against H2 (PostgreSQL mode); Flyway V7 applies to PostgreSQL at
  runtime.
