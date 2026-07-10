package io.github.ryu200o.eduworkshop.room.internal.domain.model.entity;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.state.RoomState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root of the Room module.
 *
 * <p>Encapsulates the physical identity and static operating state of a venue. It is a Rich Domain
 * Model: state mutations are only possible through explicit, intention-revealing behaviors, never
 * through public setters.</p>
 *
 * <p>Lifecycle: a room is born {@link RoomState#ACTIVE}; it may move to {@link RoomState#MAINTENANCE}
 * and back to {@link RoomState#ACTIVE}; it may be {@link RoomState#DEACTIVATED} permanently. A
 * deactivated room is frozen and rejects any further transition.</p>
 */
public class Room {

    private final UUID id;
    private final String name;
    private final int capacity;
    private final String location;
    private RoomState state;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<RoomDomainEvent> recordedEvents = new ArrayList<>();

    private Room(UUID id, String name, int capacity, String location, RoomState state, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.location = location;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory: creates a new room with the default physical state {@link RoomState#ACTIVE}
     * and emits a {@link RoomCreated} event.
     */
    public static Room create(String name, int capacity, String location) {
        Instant now = Instant.now();
        return create(UUID.randomUUID(), name, capacity, location, now, now);
    }

    /**
     * Factory with explicit identity/timestamps — used when minting a new room from externally
     * supplied identifiers. Emits a {@link RoomCreated} event.
     */
    public static Room create(UUID id, String name, int capacity, String location, Instant createdAt, Instant updatedAt) {
        requireNonBlankName(name);
        requirePositiveCapacity(capacity);
        requireNonBlankLocation(location);

        Room room = new Room(id, name, capacity, location, RoomState.ACTIVE, createdAt, updatedAt);
        room.recordedEvents.add(new RoomCreated(
                room.id, room.name, room.capacity, room.location, room.state, room.createdAt));
        return room;
    }

    /**
     * Reconstructs an existing aggregate from persisted state. Pure data mapping only:
     * it must NOT impose creation rules nor record any event (no historical event re-dispatch).
     */
    public static Room reconstruct(UUID id, String name, int capacity, String location,
                                   RoomState state, Instant createdAt, Instant updatedAt) {
        requireNonBlankName(name);
        requirePositiveCapacity(capacity);
        requireNonBlankLocation(location);
        requireNonNullState(state);

        return new Room(id, name, capacity, location, state, createdAt, updatedAt);
    }

    /**
     * Places the room under maintenance. Idempotent when already in {@link RoomState#MAINTENANCE}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void placeUnderMaintenance() {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, RoomState.MAINTENANCE,
                    "A deactivated room cannot be placed under maintenance; the deactivation is permanent.");
        }
        transitionTo(RoomState.MAINTENANCE);
    }

    /**
     * Reactivates the room back to normal operation after maintenance. Idempotent when
     * already {@link RoomState#ACTIVE}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void reactivate() {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, RoomState.ACTIVE,
                    "A deactivated room cannot be reactivated; the deactivation is permanent.");
        }
        transitionTo(RoomState.ACTIVE);
    }

    /**
     * Permanently deactivates the room (frozen, irreversible). Idempotent when already
     * {@link RoomState#DEACTIVATED}.
     *
     * @throws IllegalRoomStateException if the room is already deactivated
     */
    public void deactivate() {
        // Idempotent: a permanently deactivated room is already in its desired end state.
        if (state == RoomState.DEACTIVATED) {
            return;
        }
        transitionTo(RoomState.DEACTIVATED);
    }

    private void transitionTo(RoomState next) {
        if (this.state == next) {
            return; // idempotent no-op: no state change, no event
        }
        RoomState previous = this.state;
        this.state = next;
        this.updatedAt = Instant.now();
        this.recordedEvents.add(new RoomStateChanged(this.id, previous, next, this.updatedAt));
    }

    private static void requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new RoomDomainException("Room name must not be blank.");
        }
    }

    private static void requirePositiveCapacity(int capacity) {
        if (capacity <= 0) {
            throw new RoomDomainException("Room capacity must be greater than zero.");
        }
    }

    private static void requireNonBlankLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new RoomDomainException("Room location must not be blank.");
        }
    }

    private static void requireNonNullState(RoomState state) {
        if (state == null) {
            throw new RoomDomainException("Room state must not be null.");
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public String location() {
        return location;
    }

    public RoomState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Returns the domain events recorded since the aggregate was loaded/recreated.
     * The list is read-only; clear it via {@link #clearDomainEvents()} after dispatch.
     */
    public List<RoomDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearDomainEvents() {
        recordedEvents.clear();
    }
}
