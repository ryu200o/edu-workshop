package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import java.util.UUID;

/**
 * Read projection (View) for a single room's full detail. Lives on the Query side and is allowed to
 * aggregate/flatten data freely for the consumer, without any coupling to the write flow. Assembled
 * directly by the read adapter (CQRS bypass — no domain aggregate reconstruction).
 *
 * @param id       the room id
 * @param name     the canonical room name (e.g. "F.0201")
 * @param building the building/block
 * @param floor    the floor number
 * @param capacity the physical capacity
 * @param state    the physical operating state as a string (ACTIVE / MAINTENANCE / DEACTIVATED)
 */
public record RoomDetailView(
        UUID id,
        String name,
        String building,
        int floor,
        int capacity,
        String state
) {
}
