package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a room's canonical name changes. Carries the complete old/new name context
 * so consumer modules (e.g. Workshop) can react without re-querying Room. A direct rename emits
 * {@code reason = NAME_CHANGED}; relocation (which keeps the name but moves the location) emits
 * {@code reason = LOCATION_CHANGED} with {@code oldName == newName}.
 *
 * <p>The room's {@code code} is intentionally absent: it is an independent, FE-ordering-only field that
 * changes silently and emits no event.</p>
 */
public record RoomRenamedEvent(
        UUID roomId,
        RoomRenameReason reason,
        RoomName oldName,
        RoomName newName,
        RoomLocation location,
        Instant occurredAt
) implements RoomDomainEvent {
}
