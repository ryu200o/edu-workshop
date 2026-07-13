package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room's canonical name changes. Carries the complete old/new context so
 * consumer modules (e.g. Workshop) can react without re-querying Room. In this branch a direct code
 * swap emits {@code reason = CODE_CHANGED}; relocation (future) will emit {@code LOCATION_CHANGED}.
 */
public record RoomRenamedEvent(
        UUID roomId,
        RoomRenameReason reason,
        RoomName oldName,
        String oldCode,
        RoomName newName,
        String newCode,
        RoomLocation location,
        Instant occurredAt
) implements RoomDomainEvent {
}
