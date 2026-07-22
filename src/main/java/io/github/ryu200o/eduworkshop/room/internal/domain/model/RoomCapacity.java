package io.github.ryu200o.eduworkshop.room.internal.domain.model;

/**
 * Value object for a room's physical capacity (maximum number of occupants).
 * Invariant: must be a positive integer.
 */
public record RoomCapacity (int value) {
    public RoomCapacity {
        if (value <= 0) {
            throw new IllegalArgumentException("Room capacity must be greater than zero");
        }
    }

    public static RoomCapacity of(int value) {
        return new RoomCapacity(value);
    }
}
