package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for a {@link User} aggregate. Wraps the raw {@code UUID} to gain compile-time
 * type safety (a {@code UserId} cannot be passed where a different aggregate's id is expected) and to
 * keep the Domain model free of primitive obsession. Persistence stores the underlying {@code UUID};
 * the application/view boundary uses the raw {@code UUID} and the adapter converts between the two.
 *
 * <p>This is the opaque {@code userId} referenced by every other module (ADR 0020 §1.1): no other
 * module ever holds a physical FK to {@code iam_users}; they only pass this value around.</p>
 */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId must not be null.");
        }
    }

    /**
     * Generates a new user identity (client-generated, per the module's ID strategy). The generation
     * mechanism is an implementation detail and may change without affecting this public API.
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }
}