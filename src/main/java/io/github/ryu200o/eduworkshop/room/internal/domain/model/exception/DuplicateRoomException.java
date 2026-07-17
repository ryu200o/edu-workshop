package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

/**
 * Business exception raised when a room with the same name/location already exists system-wide
 * (global invariant violation). The type is domain vocabulary; it is thrown by the application
 * handler after the outbound existence check. The domain performs no IO itself.
 */
public class DuplicateRoomException extends RoomDomainException {

    public DuplicateRoomException(RoomName name, RoomLocation location) {
        super("A room named '" + name.asString() + "' already exists at " + location.asString() + ".");
    }
}
