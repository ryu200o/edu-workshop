package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
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
) implements Command<ChangeRoomCapacityCommand.Result> {

    /**
     * Lightweight write-side result for this command — carries only the fields directly affected by the
     * change (id, old/new capacity, and the update timestamp) to keep the write flow minimal.
     *
     * @param id          the updated room's id
     * @param oldCapacity the previous capacity
     * @param newCapacity the new capacity
     * @param updatedAt   the moment the change was applied
     */
    public record Result(UUID id, int oldCapacity, int newCapacity, Instant updatedAt) {
    }
}
