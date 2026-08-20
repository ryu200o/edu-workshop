package io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command to change a room's physical capacity. Raw input only — all validation/normalization is
 * performed by the Room domain value objects inside the handler.
 *
 * @param roomId      the id of the room to update
 * @param newCapacity the new capacity (positive integer; validated by the domain)
 */
public record ChangeRoomCapacityCommand(
        UUID roomId,
        int newCapacity
) implements Command {
}
