package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to schedule a time-windowed maintenance window for a room.
 *
 * @param roomId    the room to schedule maintenance for
 * @param startTime the maintenance window start (must not be null)
 * @param endTime   the maintenance window end (null = indefinite)
 * @param reason    the maintenance reason (must be at least 10 characters)
 * @param operator  the operator who created this schedule
 */
public record ScheduleRoomMaintenanceCommand(
        UUID roomId,
        Instant startTime,
        Instant endTime,
        String reason,
        String operator
) implements Command<ScheduleRoomMaintenanceCommand.Result> {

    /**
     * Lightweight write-side result.
     *
     * @param maintenanceId the created schedule's id
     * @param roomId        the room id
     * @param startTime     the maintenance window start
     * @param endTime       the maintenance window end
     * @param createdAt     the moment the schedule was created
     */
    public record Result(UUID maintenanceId, UUID roomId, Instant startTime, Instant endTime, Instant createdAt) {
    }
}
