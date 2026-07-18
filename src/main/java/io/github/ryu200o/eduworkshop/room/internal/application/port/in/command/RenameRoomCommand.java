package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
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
) implements Command<RenameRoomCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected by the
     * rename (id, the old/new code, the recomputed name, and the update timestamp) to keep the write
     * flow minimal.
     *
     * @param id        the renamed room's id
     * @param oldCode   the previous code (before the rename)
     * @param newCode   the new code (after the rename)
     * @param name      the recomputed canonical room name (e.g. "F.02LAB")
     * @param updatedAt the moment the rename was applied
     */
    public record Result(UUID id, String oldCode, String newCode, String name, Instant updatedAt) {
    }
}
