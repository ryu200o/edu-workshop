package io.github.ryu200o.eduworkshop.room.internal.domain.model;

/**
 * Value object for a room's free-form display name. Normalized to uppercase+trim on construction.
 * Invariant: non-blank. Name uniqueness at the (building, floor) level is a global invariant
 * enforced by the domain policy and the DB unique constraint.
 */
public record RoomName(String value) {

    public RoomName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank.");
        }
    }

    public static RoomName of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank.");
        }
        return new RoomName(raw.trim().toUpperCase());
    }
}
