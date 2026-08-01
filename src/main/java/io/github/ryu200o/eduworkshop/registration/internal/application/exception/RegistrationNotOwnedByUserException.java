package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.util.UUID;

/**
 * Application-layer exception raised when a user tries to cancel a registration they do not own.
 *
 * <p>The aggregate has no notion of "requester" — ownership is an application/orchestration concern,
 * enforced by the cancel handler (the requester id comes from the authenticated context / header and
 * is compared against the registration's {@code studentId}).</p>
 */
public final class RegistrationNotOwnedByUserException extends ApplicationException {

    public RegistrationNotOwnedByUserException(UUID registrationId, UUID requesterId) {
        super("Registration %s does not belong to user %s".formatted(registrationId, requesterId));
    }
}
