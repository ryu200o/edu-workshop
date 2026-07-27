package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private final RoomId id;
    private RoomName name;
    private RoomCapacity capacity;
    private RoomLocation location;
    private RoomCode code;
    private RoomState state;
    private final Instant createdAt;
    private Instant updatedAt;

    private List<RoomDomainEvent> recordedEvents = new ArrayList<>();

    private Room(RoomId id, RoomName name, RoomCapacity capacity, RoomLocation location, RoomCode code, RoomState state, Instant createdAt, Instant updatedAt) {
        this.id = requireNonNull(id, "RoomId cannot be null");
        this.name = requireNonNull(name, "RoomName cannot be null");
        this.capacity = requireNonNull(capacity, "RoomCapacity cannot be null");
        this.location = requireNonNull(location, "RoomLocation cannot be null");
        this.code = requireNonNull(code, "RoomCode cannot be null");
        this.state = requireNonNull(state, "RoomState cannot be null");
        this.createdAt = requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    /**
     * Factory that creates a new Room aggregate. Validates only local invariants (non-null fields).
     * Global uniqueness is an Application-layer concern and must be checked by the handler before
     * calling this method. Emits a {@link RoomCreated} event.
     */
    public static Room create(RoomId id, RoomName name, RoomLocation location, RoomCode code, RoomCapacity capacity,
                              Instant now) {

        Room room = new Room(id, name, capacity, location, code, RoomState.ACTIVE, now, now);

        room.recordedEvents.add(new RoomCreated(
                room.id, room.name, room.capacity, room.location, room.code, room.state, room.createdAt));

        return room;
    }

    /**
     * Reconstructs an existing aggregate from persisted state. Pure data mapping only:
     * it must NOT impose creation rules nor record any event (no historical event re-dispatch).
     */
    public static Room reconstruct(RoomId id, RoomName name, RoomLocation location, RoomCode code, RoomCapacity capacity,
                                             RoomState state, Instant createdAt, Instant updatedAt) {

        return new Room(id, name, capacity, location, code, state, createdAt, updatedAt);
    }

    /**
     * Places the room under maintenance. Idempotent when already in {@link RoomState#MAINTENANCE}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void placeUnderMaintenance(Instant now) {
        requireNonNull(now, "now cannot be null");
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, RoomState.MAINTENANCE,
                    "A deactivated room cannot be placed under maintenance; the deactivation is permanent.");
        }
        transitionTo(RoomState.MAINTENANCE, now);
    }

    /**
     * Reactivates the room back to normal operation after maintenance. Idempotent when
     * already {@link RoomState#ACTIVE}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void reactivate(Instant now) {
        requireNonNull(now, "now cannot be null");
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, RoomState.ACTIVE,
                    "A deactivated room cannot be reactivated; the deactivation is permanent.");
        }
        transitionTo(RoomState.ACTIVE, now);
    }

    /**
     * Permanently deactivates the room (frozen, irreversible). Idempotent when already
     * {@link RoomState#DEACTIVATED}.
     *
     */
    public void deactivate(Instant now) {
        requireNonNull(now, "now cannot be null");
        // Idempotent: a permanently deactivated room is already in its desired end state.
        if (state == RoomState.DEACTIVATED) {
            return;
        }
        transitionTo(RoomState.DEACTIVATED, now);
    }

    private void transitionTo(RoomState next, Instant now) {
        if (this.state == next) {
            return; // idempotent no-op: no state change, no event
        }
        RoomState previous = this.state;
        this.state = next;
        this.updatedAt = now;
        this.recordedEvents.add(new RoomStateChanged(this.id, previous, next, this.updatedAt));
    }

    /**
     * Changes the room's {@code code}. Validates only local invariants (state check, idempotency).
     * Global uniqueness must be checked by the handler before calling this method.
     * Silent mutation (no event).
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void changeCode(RoomCode newCode, Instant now) {
        requireNonNull(newCode, "newCode cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's code cannot be changed; the deactivation is permanent.");
        }

        if (newCode.equals(this.code)) {
            return;
        }

        this.code = newCode;
        this.updatedAt = now;
    }

    /**
     * Renames the room (free-form {@code name}). Validates only local invariants (state check,
     * idempotency). Global uniqueness must be checked by the handler before calling this method.
     * Emits a {@link RoomRenamedEvent}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void changeName(RoomName newName, Instant now) {

        requireNonNull(newName, "New name cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's name cannot be changed; the deactivation is permanent.");
        }

        if (newName.equals(this.name)) {
            return;
        }

        RoomName previousName = this.name;
        this.name = newName;
        this.updatedAt = now;
        this.recordedEvents.add(new RoomRenamedEvent(
                id, previousName, newName, this.updatedAt));
    }

    /**
     * Relocates the room (changes its building/floor; {@code name} and {@code code} are preserved).
     * Validates only local invariants (state check, idempotency). Global uniqueness must be checked
     * by the handler before calling this method. Emits a {@link RoomRelocatedEvent}.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     */
    public void relocateTo(RoomLocation newLocation, Instant now) {
        requireNonNull(newLocation, "newLocation cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room cannot be relocated; the deactivation is permanent.");
        }

        if (newLocation.equals(this.location)) {
            return;
        }

        RoomLocation previousLocation = this.location;
        this.location = newLocation;
        this.updatedAt = now;
        this.recordedEvents.add(new RoomRelocatedEvent(
                id, previousLocation, newLocation, this.updatedAt));
    }

    /**
     * Changes the room's physical {@code capacity}. The new value is validated by the same self-defense
     * rule used at creation (must be a positive integer), enforced instantly in RAM. Emits a
     * {@link RoomCapacityChanged} event capturing the full delta.
     *
     * <p>The {@code updatedAt} timestamp is controlled entirely by this aggregate (in RAM), never by the
     * persistence layer, so the write path owns the full state transition before it is persisted.</p>
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED} (permanently frozen)
     * @throws IllegalArgumentException  if the new capacity is not a valid positive integer (raised by
     *                                   the self-validating {@link RoomCapacity} value object)
     */
    public void changeCapacity(RoomCapacity newCapacity, Instant now) {
        // Domain only null-checks the VO; value validity is enforced by the VO itself.
        requireNonNull(newCapacity, "New capacity cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's capacity cannot be changed; the deactivation is permanent.");
        }

        // Idempotent no-op: same capacity means no change, no event, no persist.
        if (newCapacity.equals(this.capacity)) {
            return;
        }

        RoomCapacity previousCapacity = this.capacity;
        this.capacity = newCapacity;
        this.updatedAt = now;
        this.recordedEvents.add(new RoomCapacityChanged(id, previousCapacity, newCapacity, this.updatedAt));
    }

    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
    public RoomId id() {
        return id;
    }

    public RoomName name() {
        return name;
    }

    public RoomCapacity capacity() {
        return capacity;
    }

    public RoomCode code() {
        return code;
    }

    public RoomLocation location() {
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
        recordedEvents = new ArrayList<>();
    }
}
