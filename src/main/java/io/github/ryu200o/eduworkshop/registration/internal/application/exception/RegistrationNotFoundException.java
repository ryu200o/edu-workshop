package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when a requested registration cannot be found. Thrown by
 * application handlers after an empty port lookup. This is an application concern, not a domain
 * invariant.
 */
public final class RegistrationNotFoundException extends ResourceNotFoundException {

    public RegistrationNotFoundException(String field, Object value) {
        super("Registration", field, value);
    }
}
