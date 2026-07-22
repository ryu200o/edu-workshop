package io.github.ryu200o.eduworkshop.room;

import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Public inter-module communication interface for the room module.
 * This is the only surface exposed to other modules (per ADR 0010).
 */
public interface RoomExposeAPI {

    /**
     * Looks up a room's snapshot by its UUID. Returns empty if no room exists for the given id.
     * The caller's Application layer is responsible for handling the empty case (per ADR 0010 —
     * exceptions are implementation details, not part of the public contract).
     */
    Optional<RoomSnapshot> findRoomSnapshot(UUID roomId);
}
