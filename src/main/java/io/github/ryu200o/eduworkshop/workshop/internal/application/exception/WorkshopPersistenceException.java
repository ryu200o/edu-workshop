package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationPersistenceException;

/**
 * Application-layer exception for unexpected workshop persistence failures that cannot be translated
 * into a specific domain exception. Wraps the persistence cause and identifies the resource type
 * ("Workshop") for consistent error reporting.
 */
public final class WorkshopPersistenceException extends ApplicationPersistenceException {
    public WorkshopPersistenceException(Throwable cause) {
        super("Workshop", cause);
    }

    public WorkshopPersistenceException(String message, Throwable cause) {
        super("Workshop", message, cause);
    }
}
