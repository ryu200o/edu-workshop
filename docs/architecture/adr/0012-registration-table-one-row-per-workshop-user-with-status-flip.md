# ADR 0012: Registration Table — One Row Per (Workshop, User) with Status Flip

- **Status:** Accepted (2026-08-01)
- **Date:** 2026-08-01
- **Deciders:** SA + PO (scope), Lead Engineer / Architecture Guild (design)
- **Related:** ADR 0005 (Global Business Rules in Application Orchestration),
  ADR 0007 (Cross-Module Data Decoupling via Selective Snapshotting),
  ADR 0010 (Cross-Module Dependencies Are Application-Only), `docs/db/database.md` §6

---

## Context

The Registration module books a seat for a student (`user_id`) on a workshop. The original design
in `docs/db/database.md` §6 described the full vision: statuses `CONFIRMED` / `CANCELLED` /
`ATTENDED` / `NO_SHOW`, plus check-in columns (`checked_in`, `checked_in_at`, `checked_in_by`).
Phase 1 (SA+PO decision) delivers only **register and cancel**; check-in and attendance reporting
belong to later phases.

Two decisions drove the table shape:

1. **At most one active seat per (workshop, user), re-registration allowed after cancel.** This is a
   *set-based* invariant (ADR 0005) — it cannot be proven by a single aggregate. The Application
   handler does the fast-fail read; a DB unique index is the race-proof backstop.
2. **H2 portability.** The tests run on H2 in PostgreSQL mode; H2 does **not** support partial /
   filtered unique indexes. A plain unique index on the pair would block re-registration after a
   cancel, violating the business rule.

---

## Decision

### 1. One row per (workshop_id, user_id), flip `status`

Cancelling flips the row to `CANCELLED`; re-registering flips it back to `REGISTERED`
(`reactivate`). The uniqueness gate is therefore a **plain unique index**
`uk_registrations_workshop_user (workshop_id, user_id)` — portable across H2 and PostgreSQL —
while still honouring "at most one active seat per pair".

| Column | Notes |
| :--- | :--- |
| `workshop_id`, `user_id` | Logical references, no physical FK (ADR 0010; no User module — SA+PO). |
| `status` | `VARCHAR(20)` `CHECK (status IN ('REGISTERED', 'CANCELLED'))` — Phase 1 subset only. |
| `workshop_start_time` | Selective snapshot (ADR 0007) so the 24h cancellation deadline is enforced inside the Registration BC without temporal coupling to Workshop. |
| `registered_at`, `cancelled_at` | Timestamps of the lifecycle transitions; `cancelled_at` nullable. |
| `created_at`, `updated_at` | Audit timestamps (DB defaults). |

Indexes: `uk_registrations_workshop_user` (unique), `idx_registrations_workshop_id`,
`idx_registrations_user_id`, `idx_registrations_status`.

### 2. Uniqueness: Application orchestration + DB backstop

The set-based "no two active seats for the same pair" rule is orchestrated by the
`RegisterWorkshopCommandHandler` (ADR 0005): fast-fail read via `loadByWorkshopAndUser` before
calling the aggregate. The write adapter translates a `DataIntegrityViolationException` on the
unique index into `DuplicateRegistrationException` as the final backstop.

### 3. Phased implementation — `docs/db/database.md` §6 stays the target design

§6 describes the complete Registration table (attendance statuses + check-in). Phase 1 implements a
subset; the differences (no `ATTENDED`/`NO_SHOW`, no check-in columns, status names
`REGISTERED`/`CANCELLED` instead of `CONFIRMED`) exist **only** because those features are
out of scope. **§6 is therefore NOT rewritten in Phase 1** — it remains the authoritative target
design and is synchronized only once the module is complete. This ADR records the Phase-1 mapping.

### 4. No `registration_histories` table

Per the SA+PO decision, cancellation history is captured by the row's own `cancelled_at` /
`registered_at` transitions — no separate history table in Phase 1.

---

## Consequences

- Cancellation-and-re-registration is idempotent at the row level; the outbox publishes
  `RegistrationCreated` / `RegistrationReactivated` / `RegistrationCancelled` for audit.
- The plain unique index is portable and race-proof; H2 and PostgreSQL share the same DDL
  (Flyway `V8__create_registrations.sql`).
- When check-in/attendance phases land, §6 becomes implementable with additive columns/statuses;
  the unique gate and flip-status model are unaffected.
