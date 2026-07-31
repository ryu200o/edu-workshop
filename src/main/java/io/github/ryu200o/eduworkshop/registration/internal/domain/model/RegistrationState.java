package io.github.ryu200o.eduworkshop.registration.internal.domain.model;

/**
 * Lifecycle state of a registration (ticket) for a workshop.
 *
 * <p>Per the SA+PO decision, Phase 1 has exactly two states: {@code REGISTERED} (the default when a
 * student books a seat) and {@code CANCELLED}. A single DB row per (workshop, user) pair flips
 * between the two — re-registering after a cancellation reactivates the same row rather than creating
 * a second one (see ADR 0012). Later phases may add {@code ATTENDED} / {@code NO_SHOW}.</p>
 */
public enum RegistrationState {
    REGISTERED,
    CANCELLED
}
