package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when a requested room cannot be found. Thrown by the application
 * read handlers after an empty port lookup (the domain performs no IO itself). This is an application
 * concern, not a domain invariant — the domain never imports it.
 */
public final class RoomNotFoundException extends ResourceNotFoundException {

    public RoomNotFoundException(String field, Object value) {
        super("Room", field, value);
    }
}
