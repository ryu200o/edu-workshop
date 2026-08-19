package io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;

/**
 * Raised when an account-status transition is rejected by the {@code User} aggregate's local
 * invariant (e.g. verifying an email on a {@code LOCKED} account, or logging in while
 * {@code DISABLED}).
 */
public final class IllegalUserStateException extends UserDomainException {

    private final UserId userId;
    private final UserStatus currentStatus;
    private final UserStatus attemptedStatus;

    public IllegalUserStateException(UserId userId,
                                     UserStatus currentStatus,
                                     UserStatus attemptedStatus,
                                     String message) {
        super(message);
        this.userId = userId;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public UserId getUserId() {
        return userId;
    }

    public UserStatus getCurrentStatus() {
        return currentStatus;
    }

    public UserStatus getAttemptedStatus() {
        return attemptedStatus;
    }
}