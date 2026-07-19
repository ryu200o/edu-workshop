package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room's {@code location} (building and/or floor) changes. The {@code name}
 * and {@code code} are decoupled from coordinates and are preserved across a relocation, so this event
 * carries only the old/new location — never the name.
 */
public record RoomRelocatedEvent(
        UUID roomId,
        RoomLocation oldLocation,
        RoomLocation newLocation,
        Instant occurredAt
) implements RoomDomainEvent {
}
