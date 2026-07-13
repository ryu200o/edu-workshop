package io.github.ryu200o.eduworkshop.room.internal.application.port.in.command;

import java.util.UUID;

/**
 * Lightweight write-side result for the room-creation use case. Carries only the fields directly
 * affected by the command (the new room's id and canonical name) to keep the write flow minimal.
 * Lives on the Command side — contrast with the read-side {@code *View} DTOs.
 *
 * @param id   the id minted for the created room
 * @param name the canonical room name (e.g. "F.0201")
 */
public record RoomCreatedResult(
        UUID id,
        String name
) {
}
