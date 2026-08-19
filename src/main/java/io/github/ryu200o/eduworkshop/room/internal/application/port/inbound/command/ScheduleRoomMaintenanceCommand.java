package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to schedule a time-windowed maintenance window for a room.
 *
 * @param roomId         the room to schedule maintenance for
 * @param startTime      the maintenance window start (must not be null)
 * @param endTime        the maintenance window end (null = indefinite)
 * @param reason         the maintenance reason (must be at least 10 characters)
 * @param operator       the operator who created this schedule
 * @param maintenanceId  the caller-generated id of the maintenance schedule (ADR 0021)
 */
public record ScheduleRoomMaintenanceCommand(
        UUID roomId,
        Instant startTime,
        Instant endTime,
        String reason,
        String operator,
        UUID maintenanceId
) implements Command {
}
