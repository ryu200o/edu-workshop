package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command to reactivate a room back to normal operation after maintenance (MAINTENANCE → ACTIVE).
 * Raw input only — the transition guard lives in the Room aggregate; the handler only loads, delegates,
 * and persists.
 *
 * @param roomId the id of the room to reactivate
 */
public record ReactivateRoomCommand(UUID roomId) implements Command {
}
