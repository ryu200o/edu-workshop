package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command to rename a room by changing its free-form {@code name} directly (building/floor/code
 * preserved). Raw input only — all validation/normalization is performed by the {@code RoomName} value
 * object inside the handler. Name uniqueness is enforced by a fail-fast RAM check in the handler
 * (mirroring {@code uk_rooms_building_floor_name}) and, authoritatively, by the DB constraint plus the
 * race-proof gate in the write adapter.
 *
 * @param roomId the id of the room to rename
 * @param newName the new free-form room name (non-blank; validated by {@code RoomName})
 */
public record RenameRoomCommand(
        UUID roomId,
        String newName
) implements Command {
}
