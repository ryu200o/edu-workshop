package io.github.ryu200o.eduworkshop.room.contract;

import java.util.UUID;

/**
 * Cross-module contract DTO carrying a lightweight snapshot of room data.
 * Exposed via {@link io.github.ryu200o.eduworkshop.room.RoomExposeAPI} for other modules'
 * Application layer to consume. Structured data only — no pre-formatted display strings
 * (per ADR 0010: Application Anti-Corruption Layer owns the format decision).
 *
 * @param roomId   the room's unique identifier
 * @param name     the room's display name (e.g. "F.0201")
 * @param location the room's physical location (structured building + floor)
 */
public record RoomSnapshot(
        UUID roomId,
        String name,
        Location location
) {

    public record Location(String building, int floor) {

        public Location {
            if (building == null || building.isBlank()) {
                throw new IllegalArgumentException("building must not be blank");
            }
            if (floor < 0) {
                throw new IllegalArgumentException("floor must be non-negative");
            }
        }
    }
}
