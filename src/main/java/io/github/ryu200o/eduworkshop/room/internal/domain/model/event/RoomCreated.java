package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room is first created. Carries its physical snapshot.
 */
public record RoomCreated(
        UUID roomId,
        RoomName name,
        int capacity,
        RoomLocation location,
        RoomState initialState,
        Instant occurredAt
) implements RoomDomainEvent {
}
