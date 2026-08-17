package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

/**
 * Lifecycle status of a {@link User} account (ADR 0020 §1.1 / V21).
 *
 * <ul>
 *   <li>{@link #PENDING_VERIFICATION} — self-registered, waiting for the one-time verify-email token.</li>
 *   <li>{@link #ACTIVE} — can authenticate and use business APIs (subject to global RBAC + contextual
 *       authority + the {@code must_change_password} gate).</li>
 *   <li>{@link #LOCKED} — brute-force lockout or admin lock; authentication is rejected until unlocked.</li>
 *   <li>{@link #DISABLED} — admin-disabled; authentication is rejected (no time-based auto-recovery).</li>
 * </ul>
 */
public enum UserStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    DISABLED
}