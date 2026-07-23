package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopUnscheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public void publish(Instant now) {
        requireNonNull(now, "Workshop now must be assigned before publishing");
        requireState(WorkshopState.SCHEDULED, "publish");

        this.state = WorkshopState.PUBLISHED;
        this.touch(now);

        record(new WorkshopPublished(id, updatedAt));
    }

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

    public void markMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "markMaintenanceWarning");
        this.hasRoomWarning = true;
        this.touch(now);
    }

    public void clearMaintenanceWarning(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "clearMaintenanceWarning");
        this.hasRoomWarning = false;
        this.touch(now);
    }

    public void returnToDraft(Instant now) {
        requireNonNull(now, "now cannot be null");
        requireState(WorkshopState.SCHEDULED, "returnToDraft");

        this.roomReference = null;
        this.hasRoomWarning = false;
        this.state = WorkshopState.DRAFT;
        this.touch(now);

        record(new WorkshopUnscheduled(id, updatedAt));
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
