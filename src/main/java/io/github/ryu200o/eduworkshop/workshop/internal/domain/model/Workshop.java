package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCapacityAdjusted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCompleted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopInformationUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopLatePolicyUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPlanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRescheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduleUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomEvicted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopStarted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopUnplanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.RescheduleDeadlineExceededException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCompletionNotDueException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopStartNotDueException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopTitleLockedException;

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
 * ({@link #evictPlanningOnConflict}). A {@code PUBLISHED} workshop whose room gets a maintenance
 * window is flagged with an eviction notice ({@link #markRoomEvicted}) without changing state;
 * {@link #changeRoom} and {@link #reschedule} auto-reset that notice.</p>
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
    private Instant occupancyStart;
    private WorkshopCapacity capacity;
    private WorkshopLateThreshold lateThreshold;
    private boolean hasRoomWarning;
    private boolean isRoomEvicted;
    private Instant roomEvictedAt;
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
                     Instant occupancyStart,
                     WorkshopCapacity capacity,
                     WorkshopLateThreshold lateThreshold,
                     boolean hasRoomWarning,
                     boolean isRoomEvicted,
                     Instant roomEvictedAt,
                     WorkshopState state,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = requireNonNull(id, "WorkshopId cannot be null");
        this.title = requireNonNull(title, "WorkshopTitle cannot be null");
        this.description = requireNonNull(description, "WorkshopDescription cannot be null");
        this.roomReference = roomReference;
        this.startTime = requireNonNull(startTime, "startTime cannot be null");
        this.endTime = requireNonNull(endTime, "endTime cannot be null");
        this.occupancyStart = requireNonNull(occupancyStart, "occupancyStart cannot be null");
        this.capacity = requireNonNull(capacity, "capacity cannot be null");
        this.lateThreshold = requireNonNull(lateThreshold, "lateThreshold cannot be null");
        this.hasRoomWarning = hasRoomWarning;
        this.isRoomEvicted = isRoomEvicted;
        this.roomEvictedAt = roomEvictedAt;
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
     * @param id             the aggregate identifier
     * @param title          the workshop title (self-validating VO)
     * @param description    the workshop description (self-validating VO)
     * @param startTime      planned start instant
     * @param endTime        planned end instant; must be after {@code startTime}
     * @param occupancyStart the Occupancy Window start ({@code startTime − currentConfigBuffer},
     *                       ADR 0018 pure function, computed by the Application layer)
     * @param capacity       maximum participant capacity (self-validating VO)
     * @param lateThreshold  the attendance late-policy threshold — Workshop-owned persisted policy
     *                       (ADR 0019 §13.1); seeded at creation from the Application config default
     *                       (Epic 3C, OQ-3C-9)
     * @param now            the current instant, used for {@code createdAt}/{@code updatedAt}
     * @return the newly created aggregate
     * @throws InvalidWorkshopTimeRangeException if {@code endTime} is not after {@code startTime}
     */
    public static Workshop create(WorkshopId id, WorkshopTitle title, WorkshopDescription description,
                                   Instant startTime, Instant endTime, Instant occupancyStart,
                                   WorkshopCapacity capacity, WorkshopLateThreshold lateThreshold, Instant now) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidWorkshopTimeRangeException("endTime must be after startTime");
        }
        Workshop workshop = new Workshop(
                id, title, description,
                null, startTime, endTime, occupancyStart, capacity, lateThreshold,
                false, false, null, WorkshopState.DRAFT, now, now);
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
                                       Instant occupancyStart,
                                       WorkshopCapacity capacity,
                                       WorkshopLateThreshold lateThreshold,
                                       boolean hasRoomWarning,
                                       boolean isRoomEvicted,
                                       Instant roomEvictedAt,
                                       WorkshopState state,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Workshop(id, title, description, roomReference, startTime, endTime,
                occupancyStart, capacity, lateThreshold, hasRoomWarning, isRoomEvicted, roomEvictedAt, state, createdAt, updatedAt);
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
     * @param occupancyStart the new Occupancy Window start ({@code startTime − currentConfigBuffer},
     *                       ADR 0018 pure function, computed by the Application layer; room-space
     *                       mutations re-apply it per the scheduling-axis rule)
     * @param now            the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not in {@code DRAFT} or {@code PLANNED}
     */
    public void plan(RoomReference room, boolean hasRoomWarning, Instant occupancyStart, Instant now) {
        requireNonNull(room, "room must be assigned before planning");
        requireNonNull(occupancyStart, "occupancyStart cannot be null");
        requireNonNull(now, "now cannot be null");

        requireStateIn(List.of(WorkshopState.DRAFT, WorkshopState.PLANNED), "plan");

        this.roomReference = room;
        this.hasRoomWarning = hasRoomWarning;
        this.occupancyStart = occupancyStart;
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
     * Flags this workshop with an eviction notice because a room maintenance window now overlaps its
     * time slot ({@code isRoomEvicted = true}, {@code roomEvictedAt = now}).
     *
     * <p>Called by {@code RoomMaintenanceScheduledEventListener} when a maintenance schedule is
     * created for the workshop's room. The workshop's state is deliberately NOT changed — it stays
     * {@code PUBLISHED} — this is a notice, not a cancellation (Titik 2). Local guard: only a
     * {@code PUBLISHED} workshop that is not already flagged can be (re-)flagged. Records a
     * {@link WorkshopRoomEvicted} domain event (internal; no integration event — YAGNI).</p>
     *
     * @param now the current instant, used for {@code roomEvictedAt} and {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     */
    public void markRoomEvicted(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "markRoomEvicted");

        if (this.isRoomEvicted) {
            return;
        }

        this.isRoomEvicted = true;
        this.roomEvictedAt = now;
        this.touch(now);

        record(new WorkshopRoomEvicted(id, roomReference.roomId(), updatedAt));
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
     * (a {@code DRAFT} is not picked up by the overlapping-set query, which filters {@code PUBLISHED}
     * and {@code PLANNED} only) — the admin simply adjusts the time on the retained window and
     * re-plans (UX upgrade).
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
     * @param occupancyStart the new Occupancy Window start ({@code startTime − currentConfigBuffer},
     *                       ADR 0018 pure function, computed by the Application layer; room-space
     *                       mutations re-apply it per the scheduling-axis rule)
     * @param now        the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws WorkshopCapacityExceedsRoomException if the workshop capacity exceeds the new room's capacity
     */
    public void changeRoom(RoomReference newRoomRef, Instant occupancyStart, Instant now) {
        requireNonNull(newRoomRef, "new room reference must not be null");
        requireNonNull(occupancyStart, "occupancyStart cannot be null");
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "changeRoom");

        if (this.capacity.value() > newRoomRef.roomCapacitySnapshot()) {
            throw new WorkshopCapacityExceedsRoomException(this.capacity.value(), newRoomRef.roomCapacitySnapshot());
        }

        this.roomReference = newRoomRef;
        this.occupancyStart = occupancyStart;
        clearRoomEviction();
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
     * Starts a PUBLISHED workshop (→ {@code IN_PROGRESS}).
     *
     * <p>Strict start guard (D1): the session must not begin early — {@code now} must be at or
     * after {@code startTime}. This protects the late-booking {@code BOOK} flow: once the session
     * is {@code IN_PROGRESS}, the Registration gate rejects new registrations. The room is always
     * present in {@code PUBLISHED} (a publish invariant), so the emitted {@link WorkshopStarted}
     * carries the room id for downstream consumers (attendance / analytics).</p>
     *
     * @param now the current instant; must not be before {@code startTime}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws WorkshopStartNotDueException if {@code now} is before {@code startTime}
     */
    public void start(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.PUBLISHED, "start");

        if (now.isBefore(this.startTime)) {
            throw new WorkshopStartNotDueException(id, startTime, now);
        }

        this.state = WorkshopState.IN_PROGRESS;
        this.touch(now);

        record(new WorkshopStarted(id, roomReference.roomId(), updatedAt));
    }

    /**
     * Completes an IN_PROGRESS workshop (→ {@code COMPLETED}).
     *
     * <p>Strict completion guard (D2): the session must not be completed before it is due to end —
     * {@code now} must be at or after {@code endTime}. Once {@code COMPLETED} the workshop is
     * terminal and frozen (read-only).</p>
     *
     * @param now the current instant; must not be before {@code endTime}
     * @throws InvalidWorkshopStateException if the workshop is not {@code IN_PROGRESS}
     * @throws WorkshopCompletionNotDueException if {@code now} is before {@code endTime}
     */
    public void complete(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.IN_PROGRESS, "complete");

        if (now.isBefore(this.endTime)) {
            throw new WorkshopCompletionNotDueException(id, endTime, now);
        }

        this.state = WorkshopState.COMPLETED;
        this.touch(now);

        record(new WorkshopCompleted(id, updatedAt));
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
     * @param newStartTime       the new start instant; must be strictly in the future
     * @param newEndTime         the new end instant; must be after {@code newStartTime}
     * @param newOccupancyStart  the new Occupancy Window start ({@code newStartTime − currentConfigBuffer},
     *                           ADR 0018 pure function, computed by the Application layer)
     * @param now                the current instant, used for the deadline check and {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is not {@code PUBLISHED}
     * @throws RescheduleDeadlineExceededException if {@code now} is after {@code startTime − RESCHEDULE_DEADLINE}
     * @throws InvalidWorkshopTimeRangeException if {@code newEndTime} is not after {@code newStartTime},
     *         or {@code newStartTime} is not strictly in the future
     */
    public void reschedule(Instant newStartTime, Instant newEndTime, Instant newOccupancyStart, Instant now) {
        requireNonNull(newStartTime, "newStartTime cannot be null");
        requireNonNull(newEndTime, "newEndTime cannot be null");
        requireNonNull(newOccupancyStart, "newOccupancyStart cannot be null");
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
        this.occupancyStart = newOccupancyStart;
        clearRoomEviction();
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
     *   <li>{@code CANCELLED}, {@code IN_PROGRESS}, {@code COMPLETED}: read-only / frozen — rejected
     *       with {@link InvalidWorkshopStateException}.</li>
     * </ul>
     *
     * @param newTitle           the new title (must not be null)
     * @param newDescription     the new description (must not be null)
     * @param activeRegistrations the current count of active (REGISTERED) seats
     * @param now                the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is {@code CANCELLED}, {@code IN_PROGRESS},
     *         or {@code COMPLETED}
     * @throws WorkshopTitleLockedException if the workshop is {@code PUBLISHED} with
     *         {@code activeRegistrations > 0} and the title is being changed
     */
    public void updateInformation(WorkshopTitle newTitle, WorkshopDescription newDescription,
                                      int activeRegistrations, Instant now) {
        requireNonNull(newTitle, "newTitle cannot be null");
        requireNonNull(newDescription, "newDescription cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == WorkshopState.CANCELLED
                || state == WorkshopState.IN_PROGRESS
                || state == WorkshopState.COMPLETED) {
            throw new InvalidWorkshopStateException(
                    id, state, null,
                    "Cannot update information of a workshop in state " + state + ".");
        }

        boolean titleChanged = !this.title.equals(newTitle);
        boolean descriptionChanged = !this.description.equals(newDescription);

        // No-Op Guard: Không có thay đổi thì không phát Event thừa
        if (!titleChanged && !descriptionChanged) {
            return;
        }

        if (titleChanged && state == WorkshopState.PUBLISHED && activeRegistrations > 0) {
            throw new WorkshopTitleLockedException(id, activeRegistrations);
        }

        if (titleChanged) {
            //noinspection AssignmentToField
            this.title = newTitle;
        }
        if (descriptionChanged) {
            //noinspection AssignmentToField
            this.description = newDescription;
        }

        this.touch(now);

        record(new WorkshopInformationUpdated(id, newTitle.value(), newDescription.value(), updatedAt));
    }

    /**
     * Updates the attendance late-policy threshold (Epic 3C — the Workshop owns the attendance
     * policy, ADR 0019 §13.1).
     *
     * <p><strong>Negative guard (Epic 3C §4 lifecycle):</strong> the policy is mutable only in
     * {@code DRAFT}/{@code PLANNED}/{@code PUBLISHED}; from {@code IN_PROGRESS}/{@code COMPLETED}/
     * {@code CANCELLED} it is frozen and the mutation is rejected with
     * {@link InvalidWorkshopStateException} (HTTP 409). A change to the same value is a no-op
     * (no event, mirroring {@link #updateInformation}). The threshold is evaluated <em>live</em> at
     * check-in time (OQ-3C-10) — no snapshot flows to the Attendance module.</p>
     *
     * @param newThreshold the new self-validating threshold (0..86400 seconds, {@code WorkshopLateThreshold})
     * @param now          the current instant, used for {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is {@code IN_PROGRESS}/{@code COMPLETED}/{@code CANCELLED}
     */
    public void updateLatePolicy(WorkshopLateThreshold newThreshold, Instant now) {
        requireNonNull(newThreshold, "newThreshold cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == WorkshopState.IN_PROGRESS
                || state == WorkshopState.COMPLETED
                || state == WorkshopState.CANCELLED) {
            throw new InvalidWorkshopStateException(
                    id, state, null,
                    "Cannot update the late policy of a workshop in state " + state + ".");
        }

        // No-Op Guard: same value → no spurious event
        if (this.lateThreshold.equals(newThreshold)) {
            return;
        }

        this.lateThreshold = newThreshold;
        this.touch(now);

        record(new WorkshopLatePolicyUpdated(id, newThreshold.seconds(), updatedAt));
    }

    /**
     * Updates the time window of a pre-publish workshop.
     *
     * <p>Only allowed in {@code DRAFT} and {@code PLANNED} states. Post-publish
     * schedule changes must go through {@link #reschedule} (which enforces the 24h
     * deadline). When called in {@code PLANNED}, the existing {@code roomReference}
     * is kept — room conflict checking is deferred to publish time (ADR 0008).</p>
     *
     * @param newStartTime       the new start instant; must be strictly in the future
     * @param newEndTime         the new end instant; must be after {@code newStartTime}
     * @param newOccupancyStart  the new Occupancy Window start ({@code newStartTime − currentConfigBuffer},
     *                           ADR 0018 pure function, computed by the Application layer)
     * @param now                the current instant, used for the deadline check and {@code updatedAt}
     * @throws InvalidWorkshopStateException if the workshop is {@code PUBLISHED} or {@code CANCELLED}
     * @throws InvalidWorkshopTimeRangeException if {@code newEndTime} is not after {@code newStartTime},
     *         or {@code newStartTime} is not strictly in the future
     */
    public void updateSchedule(Instant newStartTime, Instant newEndTime, Instant newOccupancyStart, Instant now) {
        requireNonNull(newStartTime, "newStartTime cannot be null");
        requireNonNull(newEndTime, "newEndTime cannot be null");
        requireNonNull(newOccupancyStart, "newOccupancyStart cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == WorkshopState.PUBLISHED) {
            throw new InvalidWorkshopStateException(
                    id, state, WorkshopState.DRAFT,
                    "Cannot update schedule of a PUBLISHED workshop; use reschedule instead.");
        }

        requireStateIn(List.of(WorkshopState.DRAFT, WorkshopState.PLANNED), "updateSchedule");

        // No-Op Guard
        if (this.startTime.equals(newStartTime) && this.endTime.equals(newEndTime)) {
            return;
        }
        if (!newEndTime.isAfter(newStartTime)) {
            throw new InvalidWorkshopTimeRangeException("newEndTime must be after newStartTime");
        }
        if (!newStartTime.isAfter(now)) {
            throw new InvalidWorkshopTimeRangeException("newStartTime must be in the future");
        }

        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.occupancyStart = newOccupancyStart;
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

    /**
     * Clears the eviction notice ({@code isRoomEvicted = false}, {@code roomEvictedAt = null}).
     * Called by the self-healing auto-reset rules in {@link #changeRoom} and {@link #reschedule}:
     * once the workshop is moved to a different room or to a non-overlapping window, the notice no
     * longer applies. Does not emit a domain event (silent reset).
     */
    private void clearRoomEviction() {
        if (this.isRoomEvicted) {
            this.isRoomEvicted = false;
            this.roomEvictedAt = null;
        }
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

    /**
     * Attendance late-policy threshold — the Workshop-owned persisted policy (ADR 0019 §13.1,
     * Epic 3C). A learner checking in no later than {@code startTime + lateThreshold.seconds()} is
     * {@code ATTENDED}, otherwise {@code LATE}.
     */
    public WorkshopLateThreshold lateThreshold() {
        return lateThreshold;
    }

    /**
     * Start of the Occupancy Window (ADR 0018): {@code startTime − currentConfigBuffer}, computed by
     * the Application layer via the pure function and persisted. The room is considered occupied
     * from this instant — buffer included.
     */
    public Instant occupancyStart() {
        return occupancyStart;
    }

    public boolean hasRoomWarning() {
        return hasRoomWarning;
    }

    public boolean isRoomEvicted() {
        return isRoomEvicted;
    }

    public Instant roomEvictedAt() {
        return roomEvictedAt;
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
