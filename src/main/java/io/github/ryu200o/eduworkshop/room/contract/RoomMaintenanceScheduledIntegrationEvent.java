package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event emitted when a maintenance schedule is created for a room.
 * Consumed by the Workshop module to auto-flag overlapping PUBLISHED workshops (Titik 2).
 *
 * @param maintenanceId the schedule identity
 * @param roomId        the room under maintenance
 * @param startTime     the maintenance window start
 * @param endTime       the maintenance window end (null = indefinite)
 * @param reason        the maintenance reason
 * @param occurredAt    the moment this event was recorded
 */
public record RoomMaintenanceScheduledIntegrationEvent(
        UUID maintenanceId,
        UUID roomId,
        Instant startTime,
        Instant endTime,
        String reason,
        Instant occurredAt
) implements RoomIntegrationEvent {
}
