package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Derived reservation strength of a workshop (ADR 0018 P1). {@code SOFT} at {@code PLANNED} (planning,
 * non-exclusive — ADR 0008); {@code HARD} at {@code PUBLISHED} (reservation, exclusive). Computed by
 * {@link Workshop#reservationStrength()} — not persisted.
 */
public enum ReservationStrength {
    SOFT,
    HARD
}
