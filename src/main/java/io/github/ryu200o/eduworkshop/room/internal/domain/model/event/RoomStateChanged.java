package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted whenever a room transitions between physical operating states
 * (e.g. {@code ACTIVE -> MAINTENANCE}, {@code MAINTENANCE -> ACTIVE}, {@code ACTIVE -> DEACTIVATED}).
 */
public record RoomStateChanged(
        UUID roomId,
        RoomState previousState,
        RoomState newState,
        Instant occurredAt
) implements RoomDomainEvent {
}
