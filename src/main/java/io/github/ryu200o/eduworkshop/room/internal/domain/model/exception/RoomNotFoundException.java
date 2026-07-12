package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

/**
 * Business exception raised when a requested room cannot be found. Domain vocabulary; thrown by the
 * application read handlers after an empty lookup. The domain performs no IO itself.
 */
public class RoomNotFoundException extends RoomDomainException {

    public RoomNotFoundException(String criteria) {
        super("Room not found: " + criteria);
    }
}
