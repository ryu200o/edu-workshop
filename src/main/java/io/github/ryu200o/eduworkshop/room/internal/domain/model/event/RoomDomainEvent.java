package io.github.ryu200o.eduworkshop.room.internal.domain.model.event;

/**
 * Marker interface for all domain events emitted by the Room aggregate.
 *
 * <p>Sealed so the set of event types is closed and known at compile time, enabling exhaustive
 * pattern matching (Java 25) at the point of dispatch without falling back to {@code Object}.</p>
 */
public sealed interface RoomDomainEvent permits RoomCreated, RoomStateChanged, RoomRenamedEvent {
}
