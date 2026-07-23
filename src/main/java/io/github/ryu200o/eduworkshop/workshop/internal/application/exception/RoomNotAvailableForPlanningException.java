package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import java.util.UUID;

public final class RoomNotAvailableForPlanningException extends RuntimeException {

    public RoomNotAvailableForPlanningException(UUID roomId, String reason) {
        super("Room " + roomId + " is not available for planning: " + reason);
    }
}
