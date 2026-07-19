package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room's free-form {@code name} changes. Carries the complete old/new name
 * context so consumer modules (e.g. Workshop) can react without re-querying Room. Relocation is a
 * separate concern and emits {@link RoomRelocatedEvent} instead — this event is purely about the name.
 */
public record RoomRenamedEvent(
        UUID roomId,
        RoomName oldName,
        RoomName newName,
        Instant occurredAt
) implements RoomDomainEvent {
}
