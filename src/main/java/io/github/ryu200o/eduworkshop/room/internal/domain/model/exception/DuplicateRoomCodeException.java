package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;

/**
 * Business exception raised when a room would collide with an existing one on the global
 * {@code (building, floor, code)} coordinate invariant. It is domain vocabulary, thrown by the
 * aggregate itself (via the injected {@link RoomUniquenessPolicy}), and carries only the data needed
 * to describe the collision — the location and the occupied code.
 */
public class DuplicateRoomCodeException extends RoomDomainException {

    public DuplicateRoomCodeException(RoomLocation location, RoomCode code) {
        super("A room with code " + code.value() + " already exists at " + location.asString() + ".");
    }
}
