package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command to create a new room. Raw input parameters only — all validation/normalization is
 * performed by the Room domain value objects inside the handler.
 *
 * @param building the building/block name (will be normalized by {@code RoomLocation})
 * @param floor    the floor number (positive; validated by {@code RoomLocation})
 * @param code     the independent integer room code used for FE ordering (validated by {@code Room})
 * @param name     the free-form display name (non-blank; validated by {@code RoomName})
 * @param capacity the physical capacity (positive; validated by {@code Room})
 */
public record CreateRoomCommand(
        String building,
        int floor,
        int code,
        String name,
        int capacity
) implements Command<CreateRoomCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected
     * (the new room's id and canonical name) to keep the write flow minimal.
     *
     * @param id   the id minted for the created room
     * @param name the canonical room name (e.g. "F.0201")
     */
    public record Result(UUID id, String name) {
    }
}
