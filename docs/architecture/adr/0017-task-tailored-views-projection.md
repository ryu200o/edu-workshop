# ADR 0017: Task-Tailored Views Projection (CQRS Query-Side Views)

* **Status**: ACCEPTED
* **Date**: 2026-08-06
* **Deciders**: Solution Architect (SA), Lead Engineer
* **Technical Domain**: CQRS Read Side, View DTO / Projection Design, Over-fetching Control, Interface Segregation

---

## 1. Context & Problem Statement

The read side (`*Reader` outbound ports + `*ExposeAPI` lookups) projects data into **View DTOs** (e.g. `WorkshopSummaryView`, `WorkshopDetailView`, `WorkshopIdView`). These DTOs are consumed by two fundamentally different kinds of clients:

1. **Presentation clients** (UI / REST read models): need rich fields — titles, descriptions, room names, time ranges, states, counts.
2. **Automation / processing clients** (background jobs, scheduled tasks, integration event consumers, internal orchestration): need only **identifiers** (and occasionally a narrow predicate field) to dispatch a command or look up an aggregate.

The two client classes were historically served by **shared View DTOs**. A background job consumed the same `WorkshopSummaryView` that the UI uses, forcing the query-side to materialize (and the DB to transport) columns the job never reads — **over-fetching**.

Over-fetching matters because the Query-side is not just a UX projection layer: it is also the data path for trusted automation. A lean `WorkshopIdView` vs a rich `WorkshopSummaryView` is not a cosmetic choice — it is a contract about *how much data a task is allowed to demand*.

### Observed anti-pattern (motivating example)

Before this ADR, `WorkshopReader.getPublishedDueToStart/getInProgressDueToComplete/getPublishedOverdueByEndTime` returned `List<WorkshopSummaryView>` to the `WorkshopLifecycleJob`. The job only ever read `view.id()` to dispatch `StartWorkshopCommand` / `CompleteWorkshopCommand` / `CatchUpWorkshopCommand`. The full summary (state, room snapshot, time window) was transported and dropped.

### Why this needs to be documented

- **Boundary between presentation and automation**: the Query-side must not couple UI read models to background-job read models via one fat DTO.
- **Performance hygiene**: SQL columns are cheap to over-select on small data; they are not free at scale. The rule makes lean projections the default for non-UI consumers.
- **Interface segregation**: separate "display DTO" and "identity/processing DTO" so that a DTO's shape communicates its consumer's intent.
- **Future-proofing**: new tasks copy the pattern rather than reuse a card/detail view out of laziness.

---

## 2. Decision Drivers

- **CQRS Task-Tailored Projections**: the Query-side explicitly supports *multiple* View DTOs per aggregate. There is no requirement to minimize the number of DTOs; the requirement is that each DTO is *just enough* for one business purpose.
- **Zero Over-fetching Policy**: never reuse UI-serving DTOs (Card/Detail — text columns, time metadata, aggregates) for hidden processing tasks, background jobs, or integration events.
- **Interface Segregation on Projections**: keep display DTOs and identity/processing DTOs distinct (e.g. `WorkshopIdView` vs `WorkshopSummaryView`).
- **DB Query Pushdown**: predicates/columns must be pushed into SQL (JOOQ/JPA projections); never materialize a fat row and filter/trim in memory (reinforces ADR 0002 CQRS bypass, ADR 0016).
- **Consumer-driven port naming**: a `*Reader` contract is shaped by what consumers genuinely need (see `WorkshopReader` javadoc).

---

## 3. Decision Outline

### 3.1 Rule Statements

1. **One purpose per View DTO.** A View DTO MUST be designed for exactly one business purpose (display, identity-for-dispatch, permission lookup, counting, etc.). Do not merge two purposes into one DTO to "reduce DTO count".
2. **Zero Over-fetching.** A DTO MUST NOT declare attributes that no consumer reads. Declared-and-unconsumed attributes are a defect, not a convenience.
3. **No UI DTO reuse for automation.** Background jobs, scheduled tasks, and integration-event consumers MUST NOT be served by UI Card/Detail DTOs. They MUST consume a task-tailored lean projection (typically an identity view).
4. **SQL column pushdown.** The adapter MUST select only the columns that map to the DTO's declared attributes (no `SELECT *` + in-memory trimming).
5. **DTO name encodes intent.** Suffix a lean identity projection with `IdView` (e.g. `WorkshopIdView`); keep rich display projections with names that reflect their content (`SummaryView`, `DetailView`).

### 3.2 Scope & Non-Scope

- Applies to **all View DTOs on the read side** in every module: `WorkshopReader`, `RoomReader`, `RegistrationReader`, and any `*ExposeAPI`-returned contract that projects read-only data.
- Applies to **DTO declaration vs actual consumption** regardless of whether the consumer is a `*QueryHandler`, a `WorkshopLifecycleJob`-style scheduled component, or an integration-event processor.
- Does **NOT** change CQRS message types (Commands/Queries/Handlers, ADR 0006) — it shapes the *payload* those messages carry, not the bus mechanics.
- Does **NOT** mandate a separate DTO when a task genuinely needs the full summary (e.g. event listeners re-publishing content) — the rule is "no *unconsumed* columns", not "no rich DTOs".

---

## 4. Consequences

### Positive

- **Background jobs stay lean**: the lifecycle scheduler transports one `UUID` per row instead of a full summary.
- **Review is mechanical**: any View DTO with a declared attribute no consumer reads, or any automation consumer wired to a Summary/Detail DTO, is flagged immediately.
- **Performance at scale**: SQL row-shape follows consumer shape; no wasted transport for internal tasks.
- **Clear intent**: `IdView` vs `SummaryView` communicates, at a glance, the kind of consumer the projection serves.

### Trade-offs / Costs

- **More DTO classes** on the read side (one per purpose). Accepted: DTOs are cheap, over-fetching is not.
- **Mapping duplication**: multiple mappers for one aggregate row shape. Accepted: JOOQ record mapping keeps each projection one function.

---

## 5. Compliance Checklist

1. Every View DTO is consumed by at least one client; every declared attribute is consumed by at least one client (verified by static search, or justified in the DTO javadoc).
2. No automation/background/integration consumer depends on a UI Card/Detail DTO.
3. Adapter SELECT lists contain exactly the columns of the target DTO.
4. New projections follow §3.1 naming and purpose rules — enforced during review.

---

## 6. Implementation Checklist

1. **This ADR** documents the Task-Tailored Views philosophy and the Zero Over-fetching Policy.
2. **Applied example (already shipped)**: `WorkshopIdView` introduced; `WorkshopReader.getPublishedDueToStart/getInProgressDueToComplete/getPublishedOverdueByEndTime` return `List<WorkshopIdView>`; `JooqWorkshopReadAdapter` selects only the `ID` column; `WorkshopLifecycleJob` consumes only ids.
3. **Audit (follow-up)**: a full query-side Views audit across modules is tracked in `.llm/query_side_views_audit_report.md`; refactors identified there are executed on a dedicated branch (`tech/query-view-hardening`).

---

## 7. Glossary

- **View DTO**: an immutable record on the read side projecting a row (or row fragment) for a consumer.
- **Task-Tailored Projection**: a DTO shaped to exactly one business purpose (display, identity, permission, etc.).
- **Over-fetching**: transporting columns (SQL or in-memory) that no consumer reads.
- **IdView**: a lean identity-only projection used to dispatch commands / drive background processing.

---

*Supersedes no prior ADR; complements ADR 0002 (CQRS bypass), ADR 0016 (read-port `get*` naming & DB Query Pushdown), ADR 0005 (application orchestration).*
