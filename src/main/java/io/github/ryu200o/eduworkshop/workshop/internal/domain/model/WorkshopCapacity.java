package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Value object for a workshop's maximum participant capacity. Invariant: must be a positive integer.
 */
public record WorkshopCapacity(int value) {

    public WorkshopCapacity {
        if (value <= 0) {
            throw new IllegalArgumentException("Workshop capacity must be greater than zero.");
        }
    }

    public static WorkshopCapacity of(int value) {
        return new WorkshopCapacity(value);
    }
}
