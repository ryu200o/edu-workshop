package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to schedule a workshop (transition DRAFT → SCHEDULED) by assigning a room.
 * Per ADR 0008, scheduling is a planning act — no room-availability conflict check is performed.
 * The handler fetches room snapshot data via RoomExposeAPI and builds the RoomReference.
 *
 * @param workshopId the id of the workshop to schedule
 * @param roomId     the id of the room to assign
 */
public record ScheduleWorkshopCommand(
        UUID workshopId,
        UUID roomId
) implements Command<ScheduleWorkshopCommand.Result> {

    /**
     * Lightweight write-side result.
     *
     * @param id        the workshop id
     * @param roomId    the assigned room id
     * @param updatedAt the timestamp of the schedule transition
     */
    public record Result(UUID id, UUID roomId, Instant updatedAt) {
    }
}
