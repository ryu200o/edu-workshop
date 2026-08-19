package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

/**
 * Global RBAC role managed by the IAM module (ADR 0020 §1.2).
 *
 * <p>Every account is required to hold the base {@link #USER} role. Contextual roles
 * ({@code TRAINER}, {@code STUDENT}) are intentionally NOT listed here and NOT stored in IAM or the
 * token — they are evaluated dynamically by the consuming modules (Workshop / Registration /
 * Attendance) against internal foreign keys at command-handling time.</p>
 */
public enum GlobalRole {

    /** Mandatory base role for every account. */
    USER,

    /** Platform administrator; full admin API access. */
    ADMIN,

    /** Event coordinator / planner; may trigger {@code markAttendance} (OQ-2 mapping). */
    PLANNER,

    /** Auditor; may inspect and adjust attendance ledgers. */
    AUDITOR,

    /** Registrar; may verify registration tickets (Epic 3C). */
    VERIFIER
}