package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

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
) implements Command {
}
