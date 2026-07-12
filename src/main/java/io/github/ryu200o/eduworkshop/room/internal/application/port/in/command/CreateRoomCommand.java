package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.cqs.Command;

import java.util.UUID;

/**
 * Write command to create a new room. Raw input parameters only — all validation/normalization is
 * performed by the Room domain value objects inside the handler.
 *
 * @param building the building/block name (will be normalized by {@code RoomLocation})
 * @param floor    the floor number (positive; validated by {@code RoomLocation})
 * @param capacity the physical capacity (positive; validated by {@code Room})
 * @param roomCode the 2-digit room code (validated by {@code RoomName})
 */
public record CreateRoomCommand(
        String building,
        int floor,
        int capacity,
        String roomCode
) implements Command<UUID> {
}
