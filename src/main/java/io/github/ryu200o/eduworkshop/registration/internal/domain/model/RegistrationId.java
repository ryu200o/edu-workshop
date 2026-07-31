package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for a {@link Registration} aggregate. Wraps the raw {@code UUID} to gain
 * compile-time type safety (a {@code RegistrationId} cannot be passed where a different aggregate's
 * id is expected) and to keep the Domain model free of primitive obsession. Persistence stores the
 * underlying {@code UUID}; the application/view boundary uses the raw {@code UUID} and the adapter
 * converts between the two.
 */
public record RegistrationId(UUID value) {

    public RegistrationId {
        if (value == null) {
            throw new IllegalArgumentException("RegistrationId must not be null.");
        }
    }

    /**
     * Generates a new registration identity (client-generated, per the module's ID strategy).
     */
    public static RegistrationId generate() {
        return new RegistrationId(UUID.randomUUID());
    }

    public static RegistrationId of(UUID value) {
        return new RegistrationId(value);
    }
}
