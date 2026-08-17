package io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;

/**
 * Raised when an authentication attempt is rejected because the account is currently
 * {@code LOCKED} (escalated lockout or admin lock, ADR 0020 §1.5).
 *
 * <p>Distinct from {@link IllegalUserStateException} so the inbound adapter can map it directly to a
 * {@code 403 / 423} authentication response without losing the retry-window information.</p>
 */
public final class UserLockedException extends UserDomainException {

    private final UserId userId;
    private final Instant lockedUntil;

    public UserLockedException(UserId userId, Instant lockedUntil) {
        super("User account is locked" + (lockedUntil != null ? " until " + lockedUntil : " (admin lock)"));
        this.userId = userId;
        this.lockedUntil = lockedUntil;
    }

    public UserId getUserId() {
        return userId;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}