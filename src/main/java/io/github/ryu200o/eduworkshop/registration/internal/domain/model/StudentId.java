package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

import java.util.UUID;

/**
 * Identity of the student who registers for a workshop.
 *
 * <p>Per the SA+PO decision this is a <em>logical reference</em> (raw {@code UUID}) — the
 * Registration bounded context only needs to know <em>who</em> registered, not any personal data.
 * There is deliberately no User module / user aggregate in this project yet; a future Identity
 * Provider (Keycloak/Auth0/SSO) can be plugged in without changing this value object.</p>
 */
public record StudentId(UUID value) {

    public StudentId {
        if (value == null) {
            throw new IllegalArgumentException("StudentId must not be null.");
        }
    }

    public static StudentId of(UUID value) {
        return new StudentId(value);
    }
}
