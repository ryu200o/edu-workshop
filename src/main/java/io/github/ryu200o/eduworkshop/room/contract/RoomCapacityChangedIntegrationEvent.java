package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

public record RoomCapacityChangedIntegrationEvent(
        UUID roomId,
        int oldCapacity,
        int newCapacity,
        Instant occurredAt
) implements RoomIntegrationEvent {
}
