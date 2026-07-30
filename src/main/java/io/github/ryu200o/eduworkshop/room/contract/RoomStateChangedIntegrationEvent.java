package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

public record RoomStateChangedIntegrationEvent(
        UUID roomId,
        RoomStateContract previousState,
        RoomStateContract newState,
        Instant occurredAt
) implements RoomIntegrationEvent {
}
