package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Application-layer exception raised when an actor attempts to verify a ticket without the
 * {@code VERIFIER} role (Epic 3C, OQ-3C-1). Role authorization is enforced by the Application layer
 * from the authenticated principal. Mapped to HTTP 403.
 */
public final class RegistrationRoleViolationException extends ApplicationException {

    public RegistrationRoleViolationException(String role, String operation) {
        super("Role '%s' is not allowed to %s".formatted(role, operation));
    }
}