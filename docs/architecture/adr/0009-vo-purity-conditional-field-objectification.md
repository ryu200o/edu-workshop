# ADR 0009: VO Purity — Objectize Conditional Fields, Keep Domain a Null-Guard

- **Status:** Proposed
- **Date:** 2026-07-20
- **Deciders:** Lead Engineer / Technical Architect
- **Related:** `docs/architecture/adr/0005-domain-policy-and-global-invariant.md` (global set-based
  invariants — the *only* sanctioned exception), `docs/architecture/adr/0007-cross-module-data-decoupling-via-selective-snapshotting.md`,
  `Room.java` (current domain, with `requirePositiveCapacity` / `requireValidCode` inline checks),
  `.AGENTS.md` (Domain purity / Exception Layering).

> **Mode:** PLAN ONLY for the refactor. This ADR establishes a **cross-module design rule** (a standard),
> not the refactor implementation itself. It ships as a standalone ADR; the actual Room code changes follow
> in a later step once a sample is provided. No Java change in this commit.

---

## Context

During the Workshop domain build (and a fresh review of the Room module) we realized the aggregate layer is
**inconsistent**: some fields are Value Objects (`RoomName`, `RoomLocation`), some are bare primitives that
the domain validates inline (`capacity` via `requirePositiveCapacity`, `code` via `requireValidCode`), and
some validation intent leaks upward into the Application layer. The result is a mixed vocabulary where it is
unclear *who* owns a given invariant, and primitives quietly carry business rules that should live with the
value itself.

The inconsistency also makes the domain harder to keep pure: the aggregate ends up re-checking rules that a
VO could own, while simultaneously being the wrong place for set-based / cross-aggregate rules.

We need **one explicit standard** that decides, for every field, whether it becomes a VO and what each layer
is allowed to do.

---

## Decision

### The four rules (enforced project-wide, all modules)

```java
// ✅ QUY TẮC 1: Field có điều kiện → Object hóa thành VO
// VO tự validate nội tại của nó
```
Any field that carries a **condition** (non-blank, range, format, allowed-values, combination with siblings)
MUST be objectized into a Value Object. The VO validates its own invariant *at construction* (`of(...)` /
constructor). The rule lives with the value; callers never re-validate it.

```java
// ✅ QUY TẮC 2: Field không có điều kiện → Giữ nguyên primitive
// Không cần object hóa
```
Any field with **no condition** (a truly free value, e.g. a plain count with no bound, or an enum that is
already a closed type) stays a primitive / enum. Do NOT wrap it just for symmetry. Over-objectizing is its
own form of clutter.

```java
// ✅ QUY TẮC 3: Domain chỉ check null của VO
// Không check business rules nào khác
```
Inside an aggregate, the *only* check the domain performs on a VO field is a **null guard** (the VO must be
present). Every business rule the VO owns was already enforced when the VO was constructed. The domain must
NOT repeat, re-assert, or duplicate those rules (no `requirePositiveCapacity`, no `requireValidCode`, no
range re-checks). This is what keeps the domain a thin, always-valid state machine.

```java
// ✅ QUY TẮC 4: Application KHÔNG check business rules
// Chỉ tạo VO và gọi domain
```
The Application layer (command handlers, adapters) MUST NOT contain business-rule checks. Its job is purely
mechanical: construct the VOs (which self-validate and will throw on bad input) and hand them to the
aggregate. If validation logic appears in a handler, it belongs either in the VO (rule 1) or, for
set-based/global concerns, in a Policy (see Exception below).

### The sanctioned exception: global / set-based invariants → Policy (ADR 0005)

Rules 1–4 govern **per-value / local** invariants. A **cross-aggregate, set-based** invariant (e.g. "no two
rooms share the same `(location, code)`" or "a published workshop owns the room window") is NOT a property of
a single value and therefore cannot live in a VO. Per **ADR 0005**, these remain in a domain-owned
**Policy** invoked by the aggregate (the policy does the IO; the rule stays in domain vocabulary). This is the
*only* accepted deviation from "domain checks nothing but null": a Policy call is a deliberate, named
invariant, not an ad-hoc inline check.

---

## Application to the Room module (what the coming refactor must do)

| Field | Current | Condition? | Action under ADR 0009 |
|-------|---------|-----------|------------------------|
| `name` | `RoomName` VO | non-blank (+ normalized) | **Already compliant** (rule 1). Keep. |
| `location` | `RoomLocation` VO (building/floor) | composition, non-blank parts | **Already compliant** (rule 1). Keep. |
| `state` | `RoomState` enum | closed set, no value rule | **Compliant** (rule 2 — enum is the type). Keep primitive/enum. |
| `capacity` | `int` primitive | must be `> 0` | **VIOLATION** → introduce `RoomCapacity` VO (`of(int)` validates `> 0`); domain drops `requirePositiveCapacity` and only null-checks the VO. |
| `code` | `int` primitive | must be `> 0` | **VIOLATION** → introduce `RoomCode` VO (`of(int)` validates `> 0`); domain drops `requireValidCode` and only null-checks the VO. |

Net effect on `Room.java`: remove `requirePositiveCapacity` / `requireValidCode` (and any other inline value
checks); keep only the **null guard** on each VO and the ADR-0005 `RoomUniquenessPolicy` call. The aggregate
shrinks to "assemble VOs + guard null + enforce state machine + consult Policy for set-based rules".

The same standard applies forward to Workshop and every future module: e.g. `WorkshopCapacity`,
`WorkshopTitle`, `WorkshopDescription`, `RoomReference` already follow rule 1; the Workshop aggregate already
only null-checks its VOs and delegates the room-availability conflict to the Application/Policy layer — so
Workshop is the reference implementation of this ADR, and Room is brought up to the same bar.

---

## Consequences

### Positive (Pros)
- **One consistent vocabulary** — every conditional field is a self-validating VO; no more "some VOs, some
  inline domain checks, some Application checks".
- **Domain stays thin & pure** — aggregates become null-guard + state machine + Policy calls, never rule
  re-implementations.
- **Fail-fast at the boundary** — bad input throws inside the VO constructor (closest to the source), not
  deep in a handler.
- **Reusable, testable rules** — each VO is unit-tested once, independent of the aggregate.
- **Clear ownership** — local rule → VO; set-based rule → Policy (ADR 0005); no third hiding place.

### Negative / Trade-offs (Cons)
- **More small VO classes** for conditioned primitives (`RoomCapacity`, `RoomCode`, …). Mitigated by rule 2:
  only *conditioned* fields are objectized, so we avoid gratuitous wrappers.
- **Refactor churn** on existing modules (Room first) to extract primitives into VOs and strip inline checks.
  Accepted as a one-time alignment cost.

These trade-offs are **accepted**.

---

## Validation / Future Work

- This ADR is a **standard**, not an implementation. The Room refactor (extract `RoomCapacity` / `RoomCode`,
  remove inline checks, keep only null-guards + Policy) is a follow-up step, to be performed against a sample
  provided by the Lead before mass rollout.
- Add a lint/architecture test or AGENTS.md note codifying the four rules so future PRs are reviewed against
  them.
- Workshop domain (committed on `feat/workshop-domain-aggregate`) is already conformant and serves as the
  reference example.
