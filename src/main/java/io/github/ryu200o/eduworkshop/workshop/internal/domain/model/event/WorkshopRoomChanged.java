package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Domain event emitted when a PUBLISHED workshop is moved to a different room
 * ({@code Workshop.changeRoom}). The {@link RoomReference} carries the denormalized
 * name/location/capacity snapshots (ADR 0007) of the new room so consumers can react without
 * re-querying Room.
 */
public record WorkshopRoomChanged(
        WorkshopId workshopId,
        RoomReference roomReference,
        Instant occurredAt
) implements WorkshopDomainEvent {
}
