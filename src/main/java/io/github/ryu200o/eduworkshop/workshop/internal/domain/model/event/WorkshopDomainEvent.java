package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event;

/**
 * Marker interface for all domain events emitted by the Workshop aggregate.
 *
 * <p>Sealed so the set of event types is closed and known at compile time, enabling exhaustive
 * pattern matching at the point of dispatch without falling back to {@code Object}.</p>
 */
public sealed interface WorkshopDomainEvent
        permits WorkshopCreated, WorkshopScheduled, WorkshopPublished {
}
