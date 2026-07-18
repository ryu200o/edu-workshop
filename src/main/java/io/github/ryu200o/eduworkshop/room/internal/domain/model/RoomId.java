package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for a {@link Room} aggregate. Wraps the raw {@code UUID} to gain compile-time
 * type safety (a {@code RoomId} cannot be passed where a different aggregate's id is expected) and to
 * keep the Domain model free of primitive obsession. Persistence stores the underlying {@code UUID};
 * the application/view boundary uses the raw {@code UUID} and the adapter converts between the two.
 */
public record RoomId(UUID value) {

    public RoomId {
        if (value == null) {
            throw new IllegalArgumentException("RoomId must not be null.");
        }
    }

    /**
     * Generates a new random room identity (client-generated, per the module's ID strategy).
     */
    public static RoomId random() {
        return new RoomId(UUID.randomUUID());
    }

    public static RoomId of(UUID value) {
        return new RoomId(value);
    }
}
