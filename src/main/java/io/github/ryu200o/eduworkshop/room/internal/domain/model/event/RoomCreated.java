package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room is first created. Carries its physical snapshot.
 */
public record RoomCreated(
        UUID roomId,
        String name,
        int capacity,
        String location,
        RoomState initialState,
        Instant occurredAt
) implements RoomDomainEvent {
}
