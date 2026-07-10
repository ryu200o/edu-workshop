package io.github.ryu200o.eduworkshop.room.internal.domain.model.state;

/**
 * Physical / static operating state of a room (venue).
 *
 * <p>This is the ONLY state the Room module owns and persists. Temporal availability
 * (e.g. {@code AVAILABLE}, {@code OCCUPIED}) is intentionally absent here — see
 * {@code .AGENTS.md} (Domain Rule: Room State) and ADR 0001.</p>
 */
public enum RoomState {

    /** The room is physically operational and may host workshops. */
    ACTIVE,

    /** The room is temporarily out of service for repair / servicing. */
    MAINTENANCE,

    /** The room is permanently taken out of service (frozen, irreversible). */
    DEACTIVATED
}
