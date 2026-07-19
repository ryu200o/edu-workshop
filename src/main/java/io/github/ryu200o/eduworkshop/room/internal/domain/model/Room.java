package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;

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

    private final RoomId id;
    private RoomName name;
    private int capacity;
    private RoomLocation location;
    private int code;
    private RoomState state;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<RoomDomainEvent> recordedEvents = new ArrayList<>();

    private Room(RoomId id, RoomName name, int capacity, RoomLocation location, int code, RoomState state, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.location = location;
        this.code = code;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory: creates a new room with the default physical state {@link RoomState#ACTIVE}
     * and emits a {@link RoomCreated} event. The {@code name} is a free-form display string (self-defense
     * only: non-blank, normalized by {@link RoomName}); the {@code code} is an independent integer used
     * for FE ordering and is decoupled from {@code name}/{@code location}.
     *
     * <p>Uniqueness (the global {@code (location, code)} and {@code (location, name)} invariants) is
     * enforced through the injected {@link RoomUniquenessPolicy} before construction, so the decision
     * stays on the aggregate and the rule lives in domain vocabulary. The policy implementation performs
     * the IO; the aggregate remains framework/IO-free.</p>
     */
    public static Room create(RoomName name, RoomLocation location, int code, int capacity, RoomUniquenessPolicy policy) {
        Instant now = Instant.now();
        return create(RoomId.generate(), name, location, code, capacity, now, now, policy);
    }

    /**
     * Factory with explicit identity/timestamps — used when minting a new room from externally
     * supplied identifiers. Enforces the global uniqueness invariants through {@code policy} before
     * emitting a {@link RoomCreated} event.
     */
    public static Room create(RoomId id, RoomName name, RoomLocation location, int code, int capacity,
                              Instant createdAt, Instant updatedAt, RoomUniquenessPolicy policy) {
        requireNonNullName(name);
        requireValidCode(code);
        requirePositiveCapacity(capacity);
        requireNonNullLocation(location);

        // Global invariant (set-based): enforced via the domain-owned policy. Idempotency is irrelevant
        // here (brand-new aggregate) — a self-collision cannot occur, so we check unconditionally.
        if (!policy.isCodeUnique(location, code)) {
            throw new DuplicateRoomException(DuplicateRoomException.Reason.CODE, code, name, location);
        }
        if (!policy.isNameUnique(location, name)) {
            throw new DuplicateRoomException(name, location);
        }

        Room room = new Room(id, name, capacity, location, code, RoomState.ACTIVE, createdAt, updatedAt);
        room.recordedEvents.add(new RoomCreated(
                room.id.value(), room.name, room.capacity, room.location, room.code, room.state, room.createdAt));
        return room;
    }

    /**
     * Reconstructs an existing aggregate from persisted state. Pure data mapping only:
     * it must NOT impose creation rules nor record any event (no historical event re-dispatch).
     */
    public static Room reconstruct(RoomId id, RoomName name, RoomLocation location, int code, int capacity,
                                             RoomState state, Instant createdAt, Instant updatedAt) {
        requireNonNullName(name);
        requireNonNullLocation(location);
        requirePositiveCapacity(capacity);
        requireNonNullState(state);

        return new Room(id, name, capacity, location, code, state, createdAt, updatedAt);
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
        this.recordedEvents.add(new RoomStateChanged(this.id.value(), previous, next, this.updatedAt));
    }

    /**
     * Changes the room's {@code code} with the global {@code (location, code)} uniqueness invariant
     * enforced through the injected {@link RoomUniquenessPolicy}. Silent mutation (no event). The
     * idempotency skip runs before the policy check to avoid a false-positive self-collision.
     *
     * @throws IllegalRoomStateException   if the room is {@link RoomState#DEACTIVATED}
     * @throws RoomDomainException         if the new code is not positive
     * @throws DuplicateRoomException      if another room already owns the target coordinate
     */
    public void changeCode(int newCode, RoomUniquenessPolicy policy) {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's code cannot be changed; the deactivation is permanent.");
        }
        requireValidCode(newCode);

        // Idempotent no-op: same code means no change, no event, no persist, no IO.
        if (newCode == this.code) {
            return;
        }

        if (!policy.isCodeUnique(location, newCode)) {
            throw new DuplicateRoomException(DuplicateRoomException.Reason.CODE, newCode, name, location);
        }

        this.code = newCode;
        this.updatedAt = Instant.now();
    }

    /**
     * Renames the room (free-form {@code name}) with the global {@code (location, name)} uniqueness
     * invariant enforced through the injected {@link RoomUniquenessPolicy}. Emits a
     * {@link RoomRenamedEvent}. The idempotency skip runs before the policy check to avoid a
     * false-positive self-collision.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     * @throws DuplicateRoomException    if another room already owns the target name at this location
     */
    public void changeName(String newName, RoomUniquenessPolicy policy) {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's name cannot be changed; the deactivation is permanent.");
        }
        RoomName candidate = RoomName.of(newName);

        // Idempotent no-op: same name means no change, no event, no persist, no IO.
        if (candidate.equals(this.name)) {
            return;
        }

        if (!policy.isNameUnique(location, candidate)) {
            throw new DuplicateRoomException(candidate, location);
        }

        RoomName previousName = this.name;
        this.name = candidate;
        this.updatedAt = Instant.now();
        this.recordedEvents.add(new RoomRenamedEvent(
                id.value(), previousName, candidate, this.updatedAt));
    }

    /**
     * Relocates the room (changes its building/floor; {@code name} and {@code code} are preserved) with
     * the global invariants enforced through the injected {@link RoomUniquenessPolicy}. Because relocation
     * keeps both {@code code} and {@code name}, BOTH the {@code (location, code)} and
     * {@code (location, name)} pairs must be free at the target. Emits a {@link RoomRelocatedEvent}. The
     * idempotency skip runs before any policy check to avoid a false-positive self-collision.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     * @throws DuplicateRoomException    if another room already owns the target coordinate or name
     */
    public void relocateTo(RoomLocation newLocation, RoomUniquenessPolicy policy) {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room cannot be relocated; the deactivation is permanent.");
        }
        // Idempotent no-op: same location means no change, no event, no persist, no IO.
        if (newLocation.equals(this.location)) {
            return;
        }
        if (!policy.isCodeUnique(newLocation, code)) {
            throw new DuplicateRoomException(DuplicateRoomException.Reason.CODE, code, name, newLocation);
        }
        if (!policy.isNameUnique(newLocation, name)) {
            throw new DuplicateRoomException(name, newLocation);
        }
        RoomLocation previousLocation = this.location;
        this.location = newLocation;
        this.updatedAt = Instant.now();
        this.recordedEvents.add(new RoomRelocatedEvent(
                id.value(), previousLocation, newLocation, this.updatedAt));
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
     * @throws RoomDomainException       if the new capacity is not a valid positive integer
     */
    public void changeCapacity(int newCapacity) {
        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's capacity cannot be changed; the deactivation is permanent.");
        }
        // RAM self-defense: reuse the exact creation rule for educational-space capacity boundaries.
        requirePositiveCapacity(newCapacity);

        // Idempotent no-op: same capacity means no change, no event, no persist.
        if (newCapacity == this.capacity) {
            return;
        }

        int previousCapacity = this.capacity;
        this.capacity = newCapacity;
        this.updatedAt = Instant.now();
        this.recordedEvents.add(new RoomCapacityChanged(id.value(), previousCapacity, newCapacity, this.updatedAt));
    }

    private static void requireNonNullName(RoomName name) {
        if (name == null) {
            throw new RoomDomainException("Room name must not be null.");
        }
    }

    private static void requireNonNullLocation(RoomLocation location) {
        if (location == null) {
            throw new RoomDomainException("Room location must not be null.");
        }
    }

    private static void requirePositiveCapacity(int capacity) {
        if (capacity <= 0) {
            throw new RoomDomainException("Room capacity must be greater than zero.");
        }
    }

    private static void requireValidCode(int code) {
        if (code <= 0) {
            throw new RoomDomainException("Room code must be greater than zero.");
        }
    }

    private static void requireNonNullState(RoomState state) {
        if (state == null) {
            throw new RoomDomainException("Room state must not be null.");
        }
    }

    public RoomId id() {
        return id;
    }

    public RoomName name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public int code() {
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
        recordedEvents.clear();
    }
}
