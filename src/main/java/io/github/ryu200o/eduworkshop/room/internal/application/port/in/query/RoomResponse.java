package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import java.util.UUID;

/**
 * Read projection DTO for a room (CQRS read model). This is NOT the domain aggregate — it is a flat,
 * serialization-friendly view assembled directly by the read adapter, bypassing the domain.
 *
 * @param id       the room id
 * @param name     the canonical room name (e.g. "F.0201")
 * @param building the building/block
 * @param floor    the floor number
 * @param capacity the physical capacity
 * @param state    the physical operating state as a string (ACTIVE / MAINTENANCE / DEACTIVATED)
 */
public record RoomResponse(
        UUID id,
        String name,
        String building,
        int floor,
        int capacity,
        String state
) {
}
