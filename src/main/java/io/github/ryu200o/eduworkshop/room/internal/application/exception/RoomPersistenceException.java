package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationPersistenceException;

/**
 * Application-layer exception for unexpected room persistence failures that cannot be translated
 * into a specific domain exception (e.g. a non-unique-constraint database error). Wraps the
 * persistence cause and identifies the resource type ("Room") for consistent error reporting.
 */
public final class RoomPersistenceException extends ApplicationPersistenceException {
    public RoomPersistenceException (Throwable cause) {
        super("Room", cause);
    }

    public RoomPersistenceException (String message, Throwable cause) {
        super("Room", message, cause);
    }
}
