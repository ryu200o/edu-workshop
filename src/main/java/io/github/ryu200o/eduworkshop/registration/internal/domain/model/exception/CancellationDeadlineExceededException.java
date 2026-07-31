package io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;

import java.time.Instant;

/**
 * Raised when a student tries to cancel a registration after the cancellation deadline.
 *
 * <p>The deadline is a core business invariant, not a deployment parameter: cancellation is only
 * allowed while {@code now <= startTime − 24h} ({@link Registration#CANCELLATION_DEADLINE}).</p>
 */
public final class CancellationDeadlineExceededException extends RegistrationDomainException {

    private final RegistrationId registrationId;
    private final Instant deadline;
    private final Instant attemptedAt;

    public CancellationDeadlineExceededException(RegistrationId registrationId,
                                                 Instant deadline,
                                                 Instant attemptedAt) {
        super("Registration " + registrationId.value() + " can only be cancelled no later than "
                + deadline + " (24h before the workshop starts); attempted at " + attemptedAt + ".");
        this.registrationId = registrationId;
        this.deadline = deadline;
        this.attemptedAt = attemptedAt;
    }

    public RegistrationId getRegistrationId() {
        return registrationId;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
