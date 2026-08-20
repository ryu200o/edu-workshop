package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command to permanently deactivate a room (ACTIVE/MAINTENANCE → DEACTIVATED). The deactivation is
 * frozen and irreversible; the guard lives in the Room aggregate. Raw input only — the handler loads,
 * delegates, and persists.
 *
 * @param roomId the id of the room to deactivate
 */
public record DeactivateRoomCommand(UUID roomId) implements Command {
}
