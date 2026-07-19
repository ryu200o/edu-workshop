package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to relocate a room by changing its building and/or floor (the code is preserved). Raw
 * input only — all validation/normalization is performed by the Room domain value objects inside the
 * handler.
 *
 * @param roomId      the id of the room to relocate
 * @param newBuilding the new building/block name (validated by {@code RoomLocation})
 * @param newFloor    the new floor number (positive; validated by {@code RoomLocation})
 */
public record RelocateRoomCommand(
        UUID roomId,
        String newBuilding,
        int newFloor
) implements Command<RelocateRoomCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected by the
     * relocation (id, old/new location, and the update timestamp) to keep the write flow minimal.
     *
     * @param id          the relocated room's id
     * @param oldLocation the previous location
     * @param newLocation the new location
     * @param updatedAt   the moment the relocation was applied
     */
    public record Result(UUID id, RoomLocation oldLocation, RoomLocation newLocation, Instant updatedAt) {
    }
}
