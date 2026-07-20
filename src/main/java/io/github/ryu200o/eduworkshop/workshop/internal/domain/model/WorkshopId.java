package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for a {@link Workshop} aggregate. Wraps the raw {@code UUID} to gain compile-time
 * type safety (a {@code WorkshopId} cannot be passed where a different aggregate's id is expected) and to
 * keep the Domain model free of primitive obsession. Persistence stores the underlying {@code UUID};
 * the application/view boundary uses the raw {@code UUID} and the adapter converts between the two.
 */
public record WorkshopId(UUID value) {

    public WorkshopId {
        if (value == null) {
            throw new IllegalArgumentException("WorkshopId must not be null.");
        }
    }

    /**
     * Generates a new workshop identity (client-generated, per the module's ID strategy). The generation
     * mechanism is an implementation detail and may change without affecting this public API.
     */
    public static WorkshopId generate() {
        return new WorkshopId(UUID.randomUUID());
    }

    public static WorkshopId of(UUID value) {
        return new WorkshopId(value);
    }
}
