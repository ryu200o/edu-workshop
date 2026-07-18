package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to permanently deactivate a room (ACTIVE/MAINTENANCE → DEACTIVATED). The deactivation is
 * frozen and irreversible; the guard lives in the Room aggregate. Raw input only — the handler loads,
 * delegates, and persists.
 *
 * @param roomId the id of the room to deactivate
 */
public record DeactivateRoomCommand(UUID roomId) implements Command<DeactivateRoomCommand.Result> {

    /**
     * Lightweight write-side result — carries the affected room id, the previous/new physical states, and
     * the update timestamp.
     *
     * @param id            the updated room's id
     * @param previousState the physical state before the transition
     * @param newState      the physical state after the transition
     * @param updatedAt     the moment the transition was applied
     */
    public record Result(UUID id, RoomState previousState, RoomState newState, Instant updatedAt) {
    }
}
