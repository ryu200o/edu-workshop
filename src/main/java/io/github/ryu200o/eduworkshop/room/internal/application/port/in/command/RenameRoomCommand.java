package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.shared.cqs.Command;

import java.util.UUID;

/**
 * Write command to rename a room by changing its code (building/floor preserved). Raw input only —
 * all validation/normalization is performed by the Room domain value objects inside the handler.
 *
 * @param roomId the id of the room to rename
 * @param newCode the new 1–10 character alphanumeric room code (validated by {@code RoomName})
 */
public record RenameRoomCommand(
        UUID roomId,
        String newCode
) implements Command<RoomResponse> {
}
