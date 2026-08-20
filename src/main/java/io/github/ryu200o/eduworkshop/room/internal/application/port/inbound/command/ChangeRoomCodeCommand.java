package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

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
) implements Command {
}
