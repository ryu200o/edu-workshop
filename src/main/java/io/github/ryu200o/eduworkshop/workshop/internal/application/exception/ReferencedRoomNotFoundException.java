package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when the workshop references a room that does not exist.
 * This is an application concern (the handler looked up the room via RoomExposeAPI and found
 * nothing), not a domain invariant violation. Per ADR 0010, exceptions from the Room module
 * never leak into Workshop — this is Workshop's own exception type.
 */
public final class ReferencedRoomNotFoundException extends ResourceNotFoundException {

    public ReferencedRoomNotFoundException(String field, Object value) {
        super("ReferencedRoom", field, value);
    }
}
