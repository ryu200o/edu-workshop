package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;
import java.util.UUID;

public final class RoomNotAvailableForPlanningException extends ApplicationException {

    public RoomNotAvailableForPlanningException(UUID roomId, String reason) {
        super("Room " + roomId + " is not available for planning: " + reason);
    }
}
