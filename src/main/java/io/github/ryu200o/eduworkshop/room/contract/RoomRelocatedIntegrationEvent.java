package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

public record RoomRelocatedIntegrationEvent(
        UUID roomId,
        String oldLocation,
        String newLocation,
        Instant occurredAt
) implements RoomIntegrationEvent {
}
