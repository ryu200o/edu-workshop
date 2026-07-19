package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to change a room's independent {@code code} (an int used only for FE ordering / floor-map
 * rendering). This is a silent mutation: it emits NO domain event. Raw input only — validation is performed
 * by the {@code Room} aggregate inside the handler.
 *
 * @param roomId the id of the room whose code is changing
 * @param newCode the new independent integer code (positive; validated by {@code Room})
 */
public record ChangeRoomCodeCommand(
        UUID roomId,
        int newCode
) implements Command<ChangeRoomCodeCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected
     * (the room id, the old/new code, and the update timestamp) to keep the write flow minimal.
     *
     * @param id        the room's id
     * @param oldCode   the previous code (before the change)
     * @param newCode   the new code (after the change)
     * @param updatedAt the moment the code change was applied
     */
    public record Result(UUID id, int oldCode, int newCode, Instant updatedAt) {
    }
}
