package io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;

/**
 * Raised when a requested lifecycle transition is rejected by the registration's state invariant.
 *
 * <p>Examples: calling {@code cancel()} on a registration that is already {@code CANCELLED}, or
 * calling {@code reactivate()} on a registration that is still {@code REGISTERED}.</p>
 */
public final class InvalidRegistrationStateException extends RegistrationDomainException {

    private final RegistrationId registrationId;
    private final RegistrationState currentState;
    private final RegistrationState attemptedState;

    public InvalidRegistrationStateException(RegistrationId registrationId,
                                             RegistrationState currentState,
                                             RegistrationState attemptedState,
                                             String message) {
        super(message);
        this.registrationId = registrationId;
        this.currentState = currentState;
        this.attemptedState = attemptedState;
    }

    public RegistrationId getRegistrationId() {
        return registrationId;
    }

    public RegistrationState getCurrentState() {
        return currentState;
    }

    public RegistrationState getAttemptedState() {
        return attemptedState;
    }
}
