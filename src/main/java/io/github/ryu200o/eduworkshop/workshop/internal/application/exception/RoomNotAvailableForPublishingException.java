package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;

import java.util.UUID;

public class RoomNotAvailableForPublishingException extends RuntimeException {

    private final UUID roomId;
    private final RoomPlanningPermission.PlanningStatus status;
    private final String reason;

    public RoomNotAvailableForPublishingException(UUID roomId, RoomPlanningPermission.PlanningStatus status, String reason) {
        super("Room " + roomId + " is not available for publishing: " + status + " - " + reason);
        this.roomId = roomId;
        this.status = status;
        this.reason = reason;
    }

    public UUID roomId() { return roomId; }

    public RoomPlanningPermission.PlanningStatus status() { return status; }

    public String reason() { return reason; }
}
