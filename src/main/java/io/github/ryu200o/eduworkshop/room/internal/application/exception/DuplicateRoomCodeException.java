package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

public final class DuplicateRoomCodeException extends ApplicationException {

    public DuplicateRoomCodeException(RoomLocation location, RoomCode code) {
        super("A room with code " + code.value() + " already exists at " + location.asString() + ".");
    }
}
