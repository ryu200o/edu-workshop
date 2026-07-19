package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to rename a room by changing its free-form {@code name} directly (building/floor/code
 * preserved). Raw input only — all validation/normalization is performed by the {@code RoomName} value
 * object inside the handler. Name uniqueness is enforced by the DB {@code uk_rooms_building_floor_name}
 * constraint (and the race-proof gate in the write adapter); the handler does not pre-check it.
 *
 * @param roomId the id of the room to rename
 * @param newName the new free-form room name (non-blank; validated by {@code RoomName})
 */
public record RenameRoomCommand(
        UUID roomId,
        String newName
) implements Command<RenameRoomCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected by the
     * rename (id, the old/new name, and the update timestamp) to keep the write flow minimal.
     *
     * @param id        the renamed room's id
     * @param oldName   the previous name (before the rename)
     * @param newName   the new name (after the rename)
     * @param updatedAt the moment the rename was applied
     */
    public record Result(UUID id, String oldName, String newName, Instant updatedAt) {
    }
}
