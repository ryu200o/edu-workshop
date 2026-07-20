package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Business exception raised when a requested room cannot be found. Domain vocabulary; thrown by the
 * application read handlers after an empty lookup. The domain performs no IO itself.
 */
public class RoomNotFoundException extends ResourceNotFoundException {

    public RoomNotFoundException(String field, Object value) {
        super("Room", field, value);
    }
}
