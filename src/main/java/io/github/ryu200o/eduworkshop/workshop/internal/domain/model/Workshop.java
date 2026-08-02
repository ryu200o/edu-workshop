package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCapacityAdjusted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopInformationUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPlanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRescheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduleUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopUnplanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.RescheduleDeadlineExceededException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopTitleLockedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.RescheduleDeadlineExceededException;

import java.time.Duration;
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
 * <p>Lifecycle (see {@link WorkshopState}): born {@code DRAFT} → {@link #plan} to {@code PLANNED}
 * (planning only, no room reservation — ADR 0008) → {@link #publish} to {@code PUBLISHED} (the room is
 * reserved). Post-publish changes ({@link #changeRoom}, {@link #adjustCapacity}, {@link #cancel},
 * {@link #reschedule}) are only allowed in {@code PUBLISHED}. A room going {@code DEACTIVATED} returns
 * the workshop to {@code DRAFT} ({@link #returnToDraft}). A {@code PLANNED} workshop that loses its slot
 * to a {@code PUBLISHED} one is evicted back to {@code DRAFT} keeping its room
 * ({@link #evictPlanningOnConflict}).</p>
 *
 * <p>Local invariants are enforced here (state transitions, capacity vs room, time-window validity);
 * global / set-based rules (uniqueness of availability, conflict with other PUBLISHED workshops,
 * capacity vs active registrations) are orchestrated by the Application layer (ADR 0005).</p>
 */
public class Workshop {

    /**
     * A {@code PUBLISHED} workshop can only be rescheduled while {@code now <= startTime − 24h} —
     * mirroring {@code Registration.CANCELLATION_DEADLINE} — so that students relying on the
     * published schedule are not surprised at the last minute. Local business invariant, enforced
     * by {@link #reschedule} via {@link RescheduleDeadlineExceededException}.
     */
    public static final Duration RESCHEDULE_DEADLINE = Duration.ofHours(24);

    private final WorkshopId id;
    private WorkshopTitle title;
    private WorkshopDescription description;
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
     * after {@code startTime}); the room is not yet assigned (that is {@link #plan}). Records a
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
     * Assigns a room and moves the workshop DRAFT → {@code PLANNED} (or re-plans an already
     * {@code PLANNED} workshop onto a different room, e.g. A → B).
     *
     * <p>Per ADR 0008 this is a <em>planning</em> act, not a reservation: overlapping plans for the
     * same room are allowed, and no global availability check happens here. Re-planning from
     * {@code PLANNED} is allowed and deliberately performs <em>no</em> eviction of other PLANNED
     * workshops (planning is non-exclusive). The {@code hasRoomWarning} flag is carried over from the
     * Application handler (a room in {@code MAINTENANCE} still permits planning, with a warning).
     * Records a {@link WorkshopPlanned} event.</p>
     *
     * @param room           the room reference (id + name/location/capacity snapshots, ADR 0007)
     * @param hasRoomWarning whether the room is under maintenance (planning allowed, with warning)
     * @param now            the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code DRAFT} or {@code PLANNED}
     */
    public void plan(RoomReference room, boolean hasRoomWarning, Instant now) {
        requireNonNull(room, "room must be assigned before planning");
        requireNonNull(now, "now cannot be null");

        requireStateIn(List.of(WorkshopState.DRAFT, WorkshopState.PLANNED), "plan");

        this.roomReference = room;
        this.hasRoomWarning = hasRoomWarning;
        this.state = WorkshopState.PLANNED;
        this.touch(now);

        record(new WorkshopPlanned(id, room, updatedAt));
    }

    /**
     * Publishes a {@code PLANNED} workshop, turning planning into a reservation (ADR 0008).
     *
     * <p>Enforces the local invariant that the workshop capacity must not exceed the room's actual
     * physical capacity (passed in by the Application handler after querying Room). The global
     * "room free for the window" conflict check is orchestrated by the Application handler before
     * this call. Records a {@link WorkshopPublished} event.</p>
     *
     * @param now               the current instant, used for {@code updatedAt}
     * @param actualRoomCapacity the room's current physical capacity (from Room planning data)
     * @throws InvalidWorkshopStateException if the workshop is not in {@code PLANNED}
     * @throws WorkshopCapacityExceedsRoomException if the workshop capacity exceeds the room's capacity
     */
    public void publish(Instant now, int actualRoomCapacity) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PLANNED, "publish");

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
     * capacity-change integration events. Allowed in {@code PLANNED} and {@code PUBLISHED} (the
     * states where a room is assigned); does not emit a domain event.</p>
     *
     * @param updatedRef the room reference carrying the refreshed snapshots
     * @param now        the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop has no room yet (not {@code PLANNED}/{@code PUBLISHED})
     */
    public void updateRoomSnapshot(RoomReference updatedRef, Instant now) {
        requireNonNull(updatedRef, "room snapshot must not be null");
        requireNonNull(now, "now cannot be null");
        if (state != WorkshopState.PLANNED && state != WorkshopState.PUBLISHED) {
            throw new InvalidWorkshopStateException(
                    id, state, WorkshopState.PLANNED,
                    "Cannot update room snapshot in state " + state);
        }
        this.roomReference = updatedRef;
        this.touch(now);
    }

    /**
     * Flags the assigned room as under maintenance ({@code hasRoomWarning = true}).
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the room transitions to
     * {@code MAINTENANCE}. Planning state ({@code PLANNED}) is kept — maintenance is a warning, not
     * a blocker. Does not emit a domain event.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code PLANNED}
     */
    public void markMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PLANNED, "markMaintenanceWarning");
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
     * @throws InvalidWorkshopStateException if the workshop is not in {@code PLANNED}
     */
    public void clearMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PLANNED, "clearMaintenanceWarning");
        this.hasRoomWarning = false;
        this.touch(now);
    }

    /**
     * Releases the room and moves the workshop back {@code PLANNED → DRAFT}.
     *
     * <p>Called by the {@code WorkshopRoomEventHandler} when the room is {@code DEACTIVATED}
     * (planning no longer possible), and in Phase 2 by {@code ChangeWorkshopRoomCommandHandler} to
     * kick out conflicting {@code PLANNED} workshops from a target room. Clears the room reference
     * and the maintenance warning; records a {@link WorkshopUnplanned} event.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code PLANNED}
     */
    public void returnToDraft(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PLANNED, "returnToDraft");

        this.roomReference = null;
        this.hasRoomWarning = false;
        this.state = WorkshopState.DRAFT;
        this.touch(now);

        record(new WorkshopUnplanned(id, updatedAt));
    }

    /**
     * Evicts a {@code PLANNED} workshop back to {@code DRAFT} because a {@code PUBLISHED} workshop now
     * claims its time slot.
     *
     * <p>Deliberately <em>keeps</em> the room reference, the maintenance warning, and the time window
     * (a {@code DRAFT} is not counted by {@code countOverlapping}, which filters {@code PUBLISHED}
     * only) — the admin simply adjusts the time on the retained window and re-plans (UX upgrade).
     * This differs from {@link #returnToDraft} (used by {@code DELETE /plan} and room deactivation),
     * which releases the room and clears the warning. Does <em>not</em> emit a domain event: the
     * eviction is an internal side effect of a reschedule, not a user-facing transition.</p>
     *
     * @param now the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code PLANNED}
     */
    public void evictPlanningOnConflict(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PLANNED, "evictPlanningOnConflict");

        this.state = WorkshopState.DRAFT;
        this.touch(now);
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

    /**
     * Reschedules a PUBLISHED workshop to a new time window, keeping the room and all student
     * registrations.
     *
     * <p>Post-publish change. To respect the customers' schedule, rescheduling is only allowed while
     * {@code now <= startTime − RESCHEDULE_DEADLINE} (24h) — mirroring {@code Registration.cancel}.
     * The global "no other PUBLISHED workshop occupies the window in this room" conflict check is
     * orchestrated by the Application handler before this call (ADR 0005), which also evicts
     * overlapping {@code PLANNED} workshops. Records a {@link WorkshopRescheduled} event.</p>
     *
     * @param newStartTime the new start instant; must be strictly in the future
     * @param newEndTime   the new end instant; must be after {@code newStartTime}
     * @param now          the current instant, used for the deadline check and {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws RescheduleDeadlineExceededException if {@code now} is after {@code startTime − RESCHEDULE_DEADLINE}
     * @throws InvalidWorkshopTimeRangeException if {@code newEndTime} is not after {@code newStartTime},
     *         or {@code newStartTime} is not strictly in the future
     */
    public void reschedule(Instant newStartTime, Instant newEndTime, Instant now) {
        requireNonNull(newStartTime, "newStartTime cannot be null");
        requireNonNull(newEndTime, "newEndTime cannot be null");
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "reschedule");

        Instant deadline = this.startTime.minus(RESCHEDULE_DEADLINE);
        if (now.isAfter(deadline)) {
            throw new RescheduleDeadlineExceededException(id, deadline, now);
        }

        if (!newEndTime.isAfter(newStartTime)) {
            throw new InvalidWorkshopTimeRangeException("newEndTime must be after newStartTime");
        }
        if (!newStartTime.isAfter(now)) {
            throw new InvalidWorkshopTimeRangeException("newStartTime must be in the future");
        }

        Instant oldStartTime = this.startTime;
        Instant oldEndTime = this.endTime;
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.touch(now);

        record(new WorkshopRescheduled(id, oldStartTime, oldEndTime, newStartTime, newEndTime, updatedAt));
    }

    /**
     * Updates the title and/or description of a workshop.
     *
     * <p>State-based access control (ADR 0008 / design spec):</p>
     * <ul>
     *   <li>{@code DRAFT} / {@code PLANNED}: free edit of both fields.</li>
     *   <li>{@code PUBLISHED}: description is always mutable; title is locked when
     *       {@code activeRegistrations > 0} (prevents topic drift on issued tickets).
     *       Title changes are rejected with {@link WorkshopTitleLockedException}.</li>
     *   <li>{@code CANCELLED}: read-only — rejected with {@link InvalidWorkshopStateException}.</li>
     * </ul>
     *
     * @param newTitle           the new title (must not be blank; ignored when null)
     * @param newDescription     the new description (nullable; ignored when null)
     * @param activeRegistrations the current count of active (REGISTERED) seats
     * @param now                the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is {@code CANCELLED}
     * @throws WorkshopTitleLockedException if the workshop is {@code PUBLISHED} with
     *         {@code activeRegistrations > 0} and the title is being changed
     * @throws InvalidWorkshopTimeRangeException if {@code newTitle} is blank
     */
    public void updateInformation(WorkshopTitle newTitle, WorkshopDescription newDescription,
                                      int activeRegistrations, Instant now) {
        requireNonNull(newTitle, "newTitle cannot be null");
        requireNonNull(newDescription, "newDescription cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == WorkshopState.CANCELLED) {
            throw new InvalidWorkshopStateException(
                    id, state, null,
                    "Cannot update information of a CANCELLED workshop.");
        }

        boolean titleChanged = !this.title.value().equals(newTitle.value());
        if (titleChanged && state == WorkshopState.PUBLISHED && activeRegistrations > 0) {
            throw new WorkshopTitleLockedException(id, activeRegistrations);
        }

        if (titleChanged) {
            //noinspection AssignmentToField
            this.title = newTitle;
        }
        //noinspection AssignmentToField
        this.description = newDescription;
        this.touch(now);

        record(new WorkshopInformationUpdated(id, newTitle.value(), newDescription.value(), updatedAt));
    }

    /**
     * Updates the time window of a pre-publish workshop.
     *
     * <p>Only allowed in {@code DRAFT} and {@code PLANNED} states. Post-publish
     * schedule changes must go through {@link #reschedule} (which enforces the 24h
     * deadline). When called in {@code PLANNED}, the existing {@code roomReference}
     * is kept — room conflict checking is deferred to publish time (ADR 0008).</p>
     *
     * @param newStartTime the new start instant; must be strictly in the future
     * @param newEndTime   the new end instant; must be after {@code newStartTime}
     * @param now          the current instant, used for the deadline check and {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is {@code PUBLISHED} or {@code CANCELLED}
     * @throws InvalidWorkshopTimeRangeException if {@code newEndTime} is not after {@code newStartTime},
     *         or {@code newStartTime} is not strictly in the future
     */
    public void updateSchedule(Instant newStartTime, Instant newEndTime, Instant now) {
        requireNonNull(newStartTime, "newStartTime cannot be null");
        requireNonNull(newEndTime, "newEndTime cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == WorkshopState.PUBLISHED) {
            throw new InvalidWorkshopStateException(
                    id, state, WorkshopState.DRAFT,
                    "Cannot update schedule of a PUBLISHED workshop; use reschedule instead.");
        }

        requireStateIn(List.of(WorkshopState.DRAFT, WorkshopState.PLANNED), "updateSchedule");

        if (!newEndTime.isAfter(newStartTime)) {
            throw new InvalidWorkshopTimeRangeException("newEndTime must be after newStartTime");
        }
        if (!newStartTime.isAfter(now)) {
            throw new InvalidWorkshopTimeRangeException("newStartTime must be in the future");
        }

        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.touch(now);

        record(new WorkshopScheduleUpdated(id, newStartTime, newEndTime, updatedAt));
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

    private void requireStateIn(List<WorkshopState> expected, String operation) {
        if (!expected.contains(state)) {
            throw new InvalidWorkshopStateException(
                    id, state,
                    expected.getFirst(),
                    "Cannot " + operation + " a workshop in state " + state + "; expected one of " + expected + ".");
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
