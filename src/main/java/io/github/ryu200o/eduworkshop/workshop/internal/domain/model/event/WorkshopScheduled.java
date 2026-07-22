package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a workshop transitions DRAFT → SCHEDULED (a room is assigned). This is a
 * planning act per ADR 0008 — it does NOT reserve the room, and overlapping schedules for the same room
 * are allowed. The event carries the {@link RoomReference} (including the denormalized name/location
 * snapshots) so consumers can react without re-querying Room.
 */
public record WorkshopScheduled(
        WorkshopId workshopId,
        RoomReference roomReference,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
