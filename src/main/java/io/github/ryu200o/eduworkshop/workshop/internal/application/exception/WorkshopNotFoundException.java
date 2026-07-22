package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when a requested workshop cannot be found. Thrown by application
 * handlers after an empty port lookup. This is an application concern, not a domain invariant.
 */
public final class WorkshopNotFoundException extends ResourceNotFoundException {

    public WorkshopNotFoundException(String field, Object value) {
        super("Workshop", field, value);
    }
}
