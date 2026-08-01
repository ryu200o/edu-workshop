package io.github.ryu200o.eduworkshop.workshop.contract;

/**
 * Cross-module representation of a workshop's lifecycle state.
 *
 * <p>Mirrors {@code WorkshopState} but lives in the workshop module's {@code contract} named
 * interface so other modules (e.g. Registration) can consume it without reaching into
 * {@code workshop.internal} — the information-hiding boundary stays intact (per ADR 0010).
 * Adding/removing values here is a contract change and must be reviewed as such.</p>
 */
public enum WorkshopStateContract {
    DRAFT,
    PLANNED,
    PUBLISHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
