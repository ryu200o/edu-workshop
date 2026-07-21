package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room is first created. Carries its physical snapshot.
 */
public record RoomCreated(
        RoomId roomId,
        RoomName name,
        RoomCapacity capacity,
        RoomLocation location,
        RoomCode code,
        RoomState initialState,
        Instant occurredAt
) implements RoomDomainEvent {
}
