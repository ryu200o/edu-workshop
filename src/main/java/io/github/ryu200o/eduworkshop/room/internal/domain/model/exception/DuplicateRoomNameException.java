package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

/**
 * Business exception raised when a room would collide with an existing one on the global
 * {@code (building, floor, name)} pair invariant. It is domain vocabulary, thrown by the aggregate
 * itself (via the injected {@link RoomUniquenessPolicy}), and carries only the data needed to describe
 * the collision — the location and the occupied name.
 */
public final class DuplicateRoomNameException extends RoomDomainException {

    public DuplicateRoomNameException(RoomLocation location, RoomName name) {
        super("A room named '" + name.value() + "' already exists at " + location.asString() + ".");
    }
}
