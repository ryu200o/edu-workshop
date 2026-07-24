package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import java.util.UUID;

public class RoomConflictException extends RuntimeException {

    private final UUID roomId;
    private final UUID conflictingWorkshopId;

    public RoomConflictException(UUID roomId, UUID conflictingWorkshopId) {
        super("Room " + roomId + " is already reserved for the requested time window by workshop " + conflictingWorkshopId);
        this.roomId = roomId;
        this.conflictingWorkshopId = conflictingWorkshopId;
    }

    public UUID roomId() { return roomId; }

    public UUID conflictingWorkshopId() { return conflictingWorkshopId; }
}
