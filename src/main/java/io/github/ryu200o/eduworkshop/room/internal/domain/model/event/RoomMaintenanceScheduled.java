package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import java.time.Instant;

/**
 * Domain event recorded when a maintenance schedule is created for a room.
 *
 * @param maintenanceId the schedule identity
 * @param roomId        the room under maintenance
 * @param startTime     the maintenance window start
 * @param endTime       the maintenance window end (null = indefinite)
 * @param reason        the maintenance reason
 * @param occurredAt    the moment this event was recorded
 */
public record RoomMaintenanceScheduled(
        MaintenanceId maintenanceId,
        RoomId roomId,
        Instant startTime,
        Instant endTime,
        String reason,
        Instant occurredAt
) implements RoomDomainEvent {
}
