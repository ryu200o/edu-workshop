package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight write-side result for the room-rename use case. Carries only the fields directly
 * affected by the command (id, the old/new code, the recomputed name, and the update timestamp) to
 * keep the write flow minimal. Lives on the Command side — contrast with the read-side {@code *View}
 * DTOs.
 *
 * @param id        the renamed room's id
 * @param oldCode   the previous code (before the rename)
 * @param newCode   the new code (after the rename)
 * @param name      the recomputed canonical room name (e.g. "F.02LAB")
 * @param updatedAt the moment the rename was applied
 */
public record RoomRenamedResult(
        UUID id,
        String oldCode,
        String newCode,
        String name,
        Instant updatedAt
) {
}
