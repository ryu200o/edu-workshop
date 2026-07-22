package io.github.ryu200o.eduworkshop.room.internal.domain.model;

/**
 * Value object for a room's independent integer code, used for FE ordering / floor-map rendering.
 * Invariant: must be a positive integer.
 */
public record RoomCode(int value) {
    public RoomCode {
        if (value <= 0) {
            throw new IllegalArgumentException("Room code must be greater than zero");
        }
    }

    public static RoomCode of(int value) {
        return new RoomCode(value);
    }
}
