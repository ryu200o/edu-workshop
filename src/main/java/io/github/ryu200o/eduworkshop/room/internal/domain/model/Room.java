package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;

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

    private final List<RoomDomainEvent> recordedEvents = new ArrayList<>();

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
     * Factory with explicit identity/timestamps — used when minting a new room from externally
     * supplied identifiers. Enforces the global uniqueness invariants through {@code policy} before
     * emitting a {@link RoomCreated} event.
     */
    public static Room create(RoomId id, RoomName name, RoomLocation location, RoomCode code, RoomCapacity capacity,
                              Instant now, RoomUniquenessPolicy policy) {

        Room room = new Room(id, name, capacity, location, code, RoomState.ACTIVE, now, now);

        // Global invariant (set-based): enforced via the domain-owned policy. Idempotency is irrelevant
        // here (brand-new aggregate) — a self-collision cannot occur, so we check unconditionally.
        if (!policy.isCodeUnique(location, code)) {
            throw new DuplicateRoomCodeException(location, code);
        }
        if (!policy.isNameUnique(location, name)) {
            throw new DuplicateRoomNameException(location, name);
        }

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
     * Changes the room's {@code code} with the global {@code (location, code)} uniqueness invariant
     * enforced through the injected {@link RoomUniquenessPolicy}. Silent mutation (no event). The
     * idempotency skip runs before the policy check to avoid a false-positive self-collision.
     *
     * @throws IllegalRoomStateException   if the room is {@link RoomState#DEACTIVATED}
     * @throws IllegalArgumentException      if the new code is not positive (self-validation by {@link RoomCode})
     * @throws DuplicateRoomCodeException   if another room already owns the target coordinate
     */
    public void changeCode(RoomCode newCode, RoomUniquenessPolicy policy, Instant now) {
        requireNonNull(newCode, "newCode cannot be null");
        requireNonNull(policy, "policy cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's code cannot be changed; the deactivation is permanent.");
        }

        // Idempotent no-op: same code means no change, no event, no persist, no IO.
        if (newCode.equals(this.code)) {
            return;
        }

        if (!policy.isCodeUnique(location, newCode)) {
            throw new DuplicateRoomCodeException(location, newCode);
        }

        this.code = newCode;
        this.updatedAt = now;
    }

    /**
     * Renames the room (free-form {@code name}) with the global {@code (location, name)} uniqueness
     * invariant enforced through the injected {@link RoomUniquenessPolicy}. Emits a
     * {@link RoomRenamedEvent}. The idempotency skip runs before the policy check to avoid a
     * false-positive self-collision.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     * @throws DuplicateRoomNameException  if another room already owns the target name at this location
     */
    public void changeName(RoomName newName, RoomUniquenessPolicy policy, Instant now) {

        requireNonNull(newName, "New name cannot be null");
        requireNonNull(policy, "policy cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room's name cannot be changed; the deactivation is permanent.");
        }

        if (newName.equals(this.name)) {
            return;
        }

        if (!policy.isNameUnique(location, newName)) {
            throw new DuplicateRoomNameException(location, newName);
        }

        RoomName previousName = this.name;
        this.name = newName;
        this.updatedAt = now;
        this.recordedEvents.add(new RoomRenamedEvent(
                id, previousName, newName, this.updatedAt));
    }

    /**
     * Relocates the room (changes its building/floor; {@code name} and {@code code} are preserved) with
     * the global invariants enforced through the injected {@link RoomUniquenessPolicy}. Because relocation
     * keeps both {@code code} and {@code name}, BOTH the {@code (location, code)} and
     * {@code (location, name)} pairs must be free at the target. Emits a {@link RoomRelocatedEvent}. The
     * idempotency skip runs before any policy check to avoid a false-positive self-collision.
     *
     * @throws IllegalRoomStateException if the room is {@link RoomState#DEACTIVATED}
     * @throws DuplicateRoomCodeException   if another room already owns the target coordinate
     * @throws DuplicateRoomNameException  if another room already owns the target name
     */
    public void relocateTo(RoomLocation newLocation, RoomUniquenessPolicy policy,  Instant now) {
        requireNonNull(newLocation, "newLocation cannot be null");
        requireNonNull(policy, "policy cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == RoomState.DEACTIVATED) {
            throw new IllegalRoomStateException(id, state, null,
                    "A deactivated room cannot be relocated; the deactivation is permanent.");
        }
        // Idempotent no-op: same location means no change, no event, no persist, no IO.
        if (newLocation.equals(this.location)) {
            return;
        }
        if (!policy.isCodeUnique(newLocation, code)) {
            throw new DuplicateRoomCodeException(newLocation, code);
        }
        if (!policy.isNameUnique(newLocation, name)) {
            throw new DuplicateRoomNameException(newLocation, name);
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
        recordedEvents.clear();
    }
}
