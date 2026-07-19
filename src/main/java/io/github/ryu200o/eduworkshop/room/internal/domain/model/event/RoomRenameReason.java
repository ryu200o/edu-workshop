package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

/**
 * Why a room's canonical name changed. Distinguishes a direct rename
 * ({@code NAME_CHANGED}) from a relocation that moves the room to a new building and/or floor
 * ({@code LOCATION_CHANGED}). Both surface as a {@link RoomRenamedEvent} but drive different downstream
 * behavior in consumer modules (see docs/architecture/diagrams/room-workshop-event-reaction.mermaid).
 */
public enum RoomRenameReason {
    NAME_CHANGED,
    LOCATION_CHANGED
}
