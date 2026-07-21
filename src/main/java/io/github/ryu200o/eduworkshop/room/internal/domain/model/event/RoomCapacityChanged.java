package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room's physical {@code capacity} changes. Carries the full delta
 * (identity, old value, new value, timestamp) so consumer modules can react without re-querying Room.
 * Recorded inside the aggregate only — dispatch to an Event Bus / {@code RoomExposeAPI} is a deferred,
 * future integration (out of scope for the branch that introduced this event).
 */
public record RoomCapacityChanged(
        RoomId roomId,
        RoomCapacity oldCapacity,
        RoomCapacity newCapacity,
        Instant occurredAt
) implements RoomDomainEvent {
}
