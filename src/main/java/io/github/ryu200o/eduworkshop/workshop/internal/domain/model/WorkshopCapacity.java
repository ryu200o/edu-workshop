package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

/**
 * Value Object wrapping a workshop's participant capacity.
 *
 * <p>Owns the {@code > 0} invariant (RAM self-defense, mirrors {@code CHECK (capacity > 0)} and the
 * application-level validation). Because the rule lives here, neither {@code schedule()} nor
 * {@code publish()} needs to re-check it.</p>
 */
public record WorkshopCapacity(int value) {

    public WorkshopCapacity {
        if (value <= 0) {
            throw new WorkshopDomainException("Workshop capacity must be greater than zero.");
        }
    }

    public static WorkshopCapacity of(int value) {
        return new WorkshopCapacity(value);
    }
}
