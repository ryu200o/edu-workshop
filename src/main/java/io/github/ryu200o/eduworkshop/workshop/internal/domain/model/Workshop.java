package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root of the Workshop module.
 *
 * <p>Encapsulates the workshop's identity, static description, planning data and lifecycle state. It is a
 * Rich Domain Model: state mutations are only possible through explicit, intention-revealing behaviors,
 * never through public setters. The aggregate is framework-free (no Spring / JPA / IO) and owns only its
 * <em>local</em> invariants and the state machine.</p>
 *
 * <p>Lifecycle (this slice): {@code DRAFT → SCHEDULED → PUBLISHED}.</p>
 *
 * <ul>
 *   <li>{@code DRAFT}: title, description, start/end time and capacity are known; only room is missing.</li>
 *   <li>{@code SCHEDULED}: room assigned. Per ADR 0008 this is <em>planning</em> — it does NOT reserve
 *       the room, and overlapping schedules for the same room are allowed. No cross-workshop / room-availability
 *       check is performed here.</li>
 *   <li>{@code PUBLISHED}: the room is <em>reserved</em>. The global room-availability conflict check is a
 *       system-wide invariant owned by the Application layer (enforced before calling {@code publish()});
 *       the aggregate itself only guards the state transition.</li>
 * </ul>
 */
public class Workshop {

    private final WorkshopId id;
    private final WorkshopTitle title;
    private final WorkshopDescription description;
    private RoomReference roomReference;
    private Instant startTime;
    private Instant endTime;
    private WorkshopCapacity capacity;
    private WorkshopState state;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<WorkshopDomainEvent> recordedEvents = new ArrayList<>();

    private Workshop(WorkshopId id,
                     WorkshopTitle title,
                     WorkshopDescription description,
                     RoomReference roomReference,
                     Instant startTime,
                     Instant endTime,
                     WorkshopCapacity capacity,
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
        this.state = requireNonNull(state, "WorkshopState cannot be null");
        this.createdAt = requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    /**
     * Factory: creates a new workshop in {@link WorkshopState#DRAFT} and emits a {@link WorkshopCreated}
     * event. Title, description, time window and capacity are known at this point; only room is absent
     * (assigned later via {@link #schedule(RoomReference, Instant)}).
     */
    public static Workshop create(WorkshopId id, WorkshopTitle title, WorkshopDescription description,
                                   Instant startTime, Instant endTime, WorkshopCapacity capacity, Instant now) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidWorkshopTimeRangeException("endTime must be after startTime");
        }
        Workshop workshop = new Workshop(
                id, title, description,
                null, startTime, endTime, capacity,
                WorkshopState.DRAFT, now, now);
        workshop.record(new WorkshopCreated(id.value(), id, startTime, endTime, capacity, now));
        return workshop;
    }

    /**
     * Reconstitution factory: reconstructs a Workshop from persistent state. Bypasses creation validation
     * and event recording — used exclusively by the persistence adapter when loading existing aggregates.
     */
    public static Workshop reconstruct(WorkshopId id,
                                       WorkshopTitle title,
                                       WorkshopDescription description,
                                       RoomReference roomReference,
                                       Instant startTime,
                                       Instant endTime,
                                       WorkshopCapacity capacity,
                                       WorkshopState state,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Workshop(id, title, description, roomReference, startTime, endTime,
                capacity, state, createdAt, updatedAt);
    }

    /**
     * Transitions DRAFT → SCHEDULED. Assigns a room to this workshop (planning act, not reservation —
     * see ADR 0008). Room/time/capacity are locally validated (start/end/capacity were already set
     * at creation). This is a planning act — it does NOT check room availability against other workshops
     * (that is a global invariant enforced at publish time by the Application layer). Re-scheduling a
     * workshop that is already SCHEDULED is rejected; plan changes belong to a future {@code reschedule()}
     * transition. Emits {@link WorkshopScheduled}.
     */
    public void schedule(RoomReference room, Instant now) {
        requireNonNull(room, "room must be assigned before scheduling");
        requireNonNull(now, "now cannot be null");

        requireState(WorkshopState.DRAFT, "schedule");

        this.roomReference = room;
        this.state = WorkshopState.SCHEDULED;
        this.touch(now);

        record(new WorkshopScheduled(id, room, updatedAt));
    }

    /**
     * Transitions SCHEDULED → PUBLISHED. Pure transition — no re-validation of room/time/capacity (those
     * were enforced at {@code schedule()}). The global room-availability conflict check (another PUBLISHED
     * workshop already owns the room/time window) is performed by the Application layer BEFORE this method
     * is called; on conflict the aggregate is left unchanged. Emits {@link WorkshopPublished}.
     */
    public void publish(Instant now) {
        requireNonNull(now, "Workshop now must be assigned before publishing");
        requireState(WorkshopState.SCHEDULED, "publish");

        this.state = WorkshopState.PUBLISHED;
        this.touch(now);

        record(new WorkshopPublished(id, updatedAt));
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

    public WorkshopState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Returns the domain events recorded since the aggregate was created/reconstituted. The list is
     * read-only; clear it via {@link #clearDomainEvents()} after dispatch.
     */
    public List<WorkshopDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearDomainEvents() {
        recordedEvents.clear();
    }

    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
}
