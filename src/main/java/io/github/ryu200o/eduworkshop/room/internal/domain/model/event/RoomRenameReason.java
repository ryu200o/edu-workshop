package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

/**
 * Why a room's canonical name changed. Distinguishes a direct code swap
 * ({@code CODE_CHANGED}) from a future relocation that moves the room to a new building and/or floor
 * ({@code LOCATION_CHANGED}). Both surface as a {@link RoomRenamedEvent} but drive different downstream
 * behavior in consumer modules (see docs/architecture/diagrams/room-workshop-event-reaction.mermaid).
 */
public enum RoomRenameReason {
    CODE_CHANGED,
    LOCATION_CHANGED
}
