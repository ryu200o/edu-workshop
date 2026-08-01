package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCapacityAdjusted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopUnscheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root of the Workshop module.
 *
 * <p>Encapsulates a scheduled/published educational session: title, description, time window, capacity,
 * and an optional {@link RoomReference} carrying denormalized room snapshots (ADR 0007). A Rich Domain
 * Model — mutations only through explicit, intention-revealing behaviors, never public setters.</p>
 *
 * <p>Lifecycle (see {@link WorkshopState}): born {@code DRAFT} → {@link #schedule} to {@code SCHEDULED}
 * (planning only, no room reservation — ADR 0008) → {@link #publish} to {@code PUBLISHED} (the room is
 * reserved). Post-publish changes ({@link #changeRoom}, {@link #adjustCapacity}, {@link #cancel}) are
 * only allowed in {@code PUBLISHED}. A room going {@code DEACTIVATED} returns the workshop to
 * {@code DRAFT} ({@link #returnToDraft}).</p>
 *
 * <p>Local invariants are enforced here (state transitions, capacity vs room, time-window validity);
 * global / set-based rules (uniqueness of availability, conflict with other PUBLISHED workshops,
 * capacity vs active registrations) are orchestrated by the Application layer (ADR 0005).</p>
 */
public class Workshop {

    private final WorkshopId id;
    private final WorkshopTitle title;
    private final WorkshopDescription description;
    private RoomReference roomReference;
    private Instant startTime;
    private Instant endTime;
    private WorkshopCapacity capacity;
    private boolean hasRoomWarning;
    private WorkshopState state;
    private final Instant createdAt;
    private Instant updatedAt;

    private List<WorkshopDomainEvent> recordedEvents = new ArrayList<>();

    private Workshop(WorkshopId id,
                     WorkshopTitle title,
                     WorkshopDescription description,
                     RoomReference roomReference,
                     Instant startTime,
                     Instant endTime,
                     WorkshopCapacity capacity,
                     boolean hasRoomWarning,
                     WorkshopState state,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = requireNonNull(id, "WorkshopId cannot be null");
        this.title = requireNonNull(title, "WorkshopTitle cannot be null");
        this.description = requireNonNull(description, "WorkshopDescription cannot be null");
        this.roomReference = roomReference;
        this.startTime = requireNonNull(startTime, "startTime cannot be null");
        this.endTime = requireNonNull(endTime, "endTime cannot be null");
        this.capacity = requireNonNull(capacity, "capacity cannot be null");
        this.hasRoomWarning = hasRoomWarning;
        this.state = requireNonNull(state, "WorkshopState cannot be null");
        this.createdAt = requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    /**
     * Creates a new workshop aggregate in state {@code DRAFT}.
     *
     * <p>Validates the local invariant that the time window is well-formed ({@code endTime} strictly
     * after {@code startTime}); the room is not yet assigned (that is {@link #schedule}). Records a
     * {@link WorkshopCreated} domain event.</p>
     *
     * @param id          the aggregate identifier
     * @param title       the workshop title (self-validating VO)
     * @param description the workshop description (self-validating VO)
     * @param startTime   planned start instant
     * @param endTime     planned end instant; must be after {@code startTime}
     * @param capacity    maximum participant capacity (self-validating VO)
     * @param now         the current instant, used for {@code createdAt}/{@code updatedAt}
     * @return the newly created aggregate
     * @throws InvalidWorkshopTimeRangeException if {@code endTime} is not after {@code startTime}
     */
    public static Workshop create(WorkshopId id, WorkshopTitle title, WorkshopDescription description,
                                   Instant startTime, Instant endTime, WorkshopCapacity capacity, Instant now) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidWorkshopTimeRangeException("endTime must be after startTime");
        }
        Workshop workshop = new Workshop(
                id, title, description,
                null, startTime, endTime, capacity,
                false, WorkshopState.DRAFT, now, now);
        workshop.record(new WorkshopCreated(id.value(), id, startTime, endTime, capacity, now));
        return workshop;
    }

    /**
     * Reconstitutes an existing aggregate from persistence.
     *
     * <p>Bypasses <em>all</em> invariant checks — no spurious re-validation on read — mirroring the
     * reconstruction pattern of the other aggregates ({@code Room.reconstruct}). Called only by the
     * write adapter, never by business logic.</p>
     */
    public static Workshop reconstruct(WorkshopId id,
                                       WorkshopTitle title,
                                       WorkshopDescription description,
                                       RoomReference roomReference,
                                       Instant startTime,
                                       Instant endTime,
                                       WorkshopCapacity capacity,
                                       boolean hasRoomWarning,
                                       WorkshopState state,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Workshop(id, title, description, roomReference, startTime, endTime,
                capacity, hasRoomWarning, state, createdAt, updatedAt);
    }

    /**
     * Assigns a room and moves the workshop DRAFT → {@code SCHEDULED}.
     *
     * <p>Per ADR 0008 this is a <em>planning</em> act, not a reservation: overlapping schedules for the
     * same room are allowed, and no global availability check happens here. The {@code hasRoomWarning}
     * flag is carried over from the Application handler (a room in {@code MAINTENANCE} still permits
     * planning, with a warning). Records a {@link WorkshopScheduled} event.</p>
     *
     * @param room           the room reference (id + name/location/capacity snapshots, ADR 0007)
     * @param hasRoomWarning whether the room is under maintenance (planning allowed, with warning)
     * @param now            the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code DRAFT}
     */
    public void schedule(RoomReference room, boolean hasRoomWarning, Instant now) {
        requireNonNull(room, "room must be assigned before scheduling");
        requireNonNull(now, "now cannot be null");

        requireState(WorkshopState.DRAFT, "schedule");

        this.roomReference = room;
        this.hasRoomWarning = hasRoomWarning;
        this.state = WorkshopState.SCHEDULED;
        this.touch(now);

        record(new WorkshopScheduled(id, room, updatedAt));
    }

    /**
     * Publishes a {@code SCHEDULED} workshop, turning planning into a reservation (ADR 0008).
     *
     * <p>Enforces the local invariant that the workshop capacity must not exceed the room's actual
     * physical capacity (passed in by the Application handler after querying Room). The global
     * "room free for the window" conflict check is orchestrated by the Application handler before
     * this call. Records a {@link WorkshopPublished} event.</p>
     *
     * @param now               the current instant, used for {@code updatedAt}
     * @param actualRoomCapacity the room's current physical capacity (from Room planning data)
     * @throws InvalidWorkshopStateException if the workshop is not in {@code SCHEDULED}
     * @throws WorkshopCapacityExceedsRoomException if the workshop capacity exceeds the room's capacity
     */
    public void publish(Instant now, int actualRoomCapacity) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "publish");

        if (this.capacity.value() > actualRoomCapacity) {
            throw new WorkshopCapacityExceedsRoomException(this.capacity.value(), actualRoomCapacity);
        }

        this.state = WorkshopState.PUBLISHED;
        this.touch(now);

        record(new WorkshopPublished(id, updatedAt));
    }

    /**
     * Refreshes the denormalized room snapshots (name/location/capacity, ADR 0007) on an existing
     * room reference, without changing the room itself.
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the Room module emits rename / relocate /
     * capacity-change integration events. Allowed in {@code SCHEDULED} and {@code PUBLISHED} (the
     * states where a room is assigned); does not emit a domain event.</p>
     *
     * @param updatedRef the room reference carrying the refreshed snapshots
     * @param now        the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop has no room yet (not {@code SCHEDULED}/{@code PUBLISHED})
     */
    public void updateRoomSnapshot(RoomReference updatedRef, Instant now) {
        requireNonNull(updatedRef, "room snapshot must not be null");
        requireNonNull(now, "now cannot be null");
        if (state != WorkshopState.SCHEDULED && state != WorkshopState.PUBLISHED) {
            throw new InvalidWorkshopStateException(
                    id, state, WorkshopState.SCHEDULED,
                    "Cannot update room snapshot in state " + state);
        }
        this.roomReference = updatedRef;
        this.touch(now);
    }

    /**
     * Flags the assigned room as under maintenance ({@code hasRoomWarning = true}).
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the room transitions to
     * {@code MAINTENANCE}. Planning state ({@code SCHEDULED}) is kept — maintenance is a warning, not
     * a blocker. Does not emit a domain event.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code SCHEDULED}
     */
    public void markMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "markMaintenanceWarning");
        this.hasRoomWarning = true;
        this.touch(now);
    }

    /**
     * Clears the maintenance warning ({@code hasRoomWarning = false}).
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the room returns to {@code ACTIVE}
     * after maintenance. Does not emit a domain event.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code SCHEDULED}
     */
    public void clearMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "clearMaintenanceWarning");
        this.hasRoomWarning = false;
        this.touch(now);
    }

    /**
     * Releases the room and moves the workshop back {@code SCHEDULED → DRAFT}.
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the room is {@code DEACTIVATED}
     * (planning no longer possible), and in Phase 2 by {@code ChangeWorkshopRoomCommandHandler} to
     * kick out conflicting {@code SCHEDULED} workshops from a target room. Clears the room reference
     * and the maintenance warning; records a {@link WorkshopUnscheduled} event.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code SCHEDULED}
     */
    public void returnToDraft(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "returnToDraft");

        this.roomReference = null;
        this.hasRoomWarning = false;
        this.state = WorkshopState.DRAFT;
        this.touch(now);

        record(new WorkshopUnscheduled(id, updatedAt));
    }

    /**
     * Moves a PUBLISHED workshop to a different room.
     *
     * <p>Post-publish change ("đổi trả"). The room must be {@code ALLOWED} and free of conflicts —
     * those global checks are orchestrated by the Application handler (ADR 0005); this method only
     * enforces the local invariant that the workshop capacity must fit the new room's physical
     * capacity. The new {@link RoomReference} carries the denormalized name/location/capacity
     * snapshots (ADR 0007).</p>
     *
     * @param newRoomRef the new room reference (id + snapshots) to assign
     * @param now        the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws WorkshopCapacityExceedsRoomException if the workshop capacity exceeds the new room's capacity
     */
    public void changeRoom(RoomReference newRoomRef, Instant now) {
        requireNonNull(newRoomRef, "new room reference must not be null");
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "changeRoom");

        if (this.capacity.value() > newRoomRef.roomCapacitySnapshot()) {
            throw new WorkshopCapacityExceedsRoomException(this.capacity.value(), newRoomRef.roomCapacitySnapshot());
        }

        this.roomReference = newRoomRef;
        this.touch(now);

        record(new WorkshopRoomChanged(id, newRoomRef, updatedAt));
    }

    /**
     * Adjusts a PUBLISHED workshop's maximum capacity.
     *
     * <p>Post-publish change. The active-registration count is fetched by the Application handler
     * (ADR 0005) and passed in as data; the aggregate validates two local invariants: the new
     * capacity must not drop below the current active registrations, and must not exceed the room's
     * physical capacity (from the snapshot).</p>
     *
     * @param newCapacity          the new maximum participant capacity
     * @param activeRegistrations  the number of currently {@code REGISTERED} seats (from Registration)
     * @param now                  the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws WorkshopCapacityBelowRegistrationsException if {@code newCapacity} is less than {@code activeRegistrations}
     * @throws WorkshopCapacityExceedsRoomException if {@code newCapacity} exceeds the room's capacity
     */
    public void adjustCapacity(WorkshopCapacity newCapacity, int activeRegistrations, Instant now) {
        requireNonNull(newCapacity, "new capacity must not be null");
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "adjustCapacity");

        if (newCapacity.value() < activeRegistrations) {
            throw new WorkshopCapacityBelowRegistrationsException(newCapacity.value(), activeRegistrations);
        }

        if (newCapacity.value() > this.roomReference.roomCapacitySnapshot()) {
            throw new WorkshopCapacityExceedsRoomException(newCapacity.value(), this.roomReference.roomCapacitySnapshot());
        }

        this.capacity = newCapacity;
        this.touch(now);

        record(new WorkshopCapacityAdjusted(id, newCapacity, updatedAt));
    }

    /**
     * Cancels a PUBLISHED workshop (→ {@code CANCELLED}).
     *
     * <p>Post-publish change. Only allowed before the session starts — a workshop that is ongoing or
     * already finished must not be cancelled. The Application layer maps this into
     * {@code WorkshopCancelledIntegrationEvent} so Registration flips all active seats.</p>
     *
     * @param now the current instant; must be strictly before {@code startTime}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws WorkshopAlreadyStartedException if {@code now} is not before {@code startTime}
     */
    public void cancel(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "cancel");

        if (!now.isBefore(this.startTime)) {
            throw new WorkshopAlreadyStartedException(id, startTime, now);
        }

        this.state = WorkshopState.CANCELLED;
        this.touch(now);

        record(new WorkshopCancelled(id, updatedAt));
    }

    // ---------------------------------------------------------------------
    // Guards / helpers
    // ---------------------------------------------------------------------

    private void requireState(WorkshopState expected, String operation) {
        if (state != expected) {
            throw new InvalidWorkshopStateException(
                    id, state,
                    expected,
                    "Cannot " + operation + " a workshop in state " + state + "; expected " + expected + ".");
        }
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private void record(WorkshopDomainEvent event) {
        recordedEvents.add(event);
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    public WorkshopId id() {
        return id;
    }

    public WorkshopTitle title() {
        return title;
    }

    public WorkshopDescription description() {
        return description;
    }

    public RoomReference roomReference() {
        return roomReference;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public WorkshopCapacity capacity() {
        return capacity;
    }

    public boolean hasRoomWarning() {
        return hasRoomWarning;
    }

    public WorkshopState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<WorkshopDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearDomainEvents() {
        recordedEvents = new ArrayList<>();
    }

    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
}
