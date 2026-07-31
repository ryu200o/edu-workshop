package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

public record RoomRenamedIntegrationEvent(
        UUID roomId,
        String oldName,
        String newName,
        Instant occurredAt
) implements RoomIntegrationEvent {
}
