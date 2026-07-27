package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

public final class DuplicateRoomNameException extends ApplicationException {

    public DuplicateRoomNameException(RoomLocation location, RoomName name) {
        super("A room named '" + name.value() + "' already exists at " + location.asString() + ".");
    }
}
