package io.github.ryu200o.eduworkshop.workshop.internal.domain;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkshopTest {

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant END_BEFORE_START = Instant.parse("2026-09-01T08:00:00Z");
    private static final RoomReference ROOM = RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2");
    private static final WorkshopCapacity CAPACITY = WorkshopCapacity.of(30);

    private WorkshopId newId() {
        return WorkshopId.generate();
    }

    private WorkshopTitle title() {
        return WorkshopTitle.of("Spring Boot Workshop");
    }

    private WorkshopDescription description() {
        return WorkshopDescription.of("Hands-on intro to Spring Modulith.");
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Test
    void create_producesDraftWithCreatedEvent() {
        WorkshopId id = newId();
        Workshop workshop = Workshop.create(id, title(), description());

        assertThat(workshop.id()).isEqualTo(id);
        assertThat(workshop.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(workshop.title().asString()).isEqualTo("Spring Boot Workshop");
        assertThat(workshop.description().asString()).isEqualTo("Hands-on intro to Spring Modulith.");
        assertThat(workshop.roomReference()).isNull();
        assertThat(workshop.startTime()).isNull();
        assertThat(workshop.capacity()).isNull();

        assertThat(workshop.recordedEvents())
                .hasSize(1)
                .hasOnlyElementsOfType(WorkshopCreated.class);
    }

    @Test
    void create_rejectsBlankTitle() {
        assertThatThrownBy(() -> Workshop.create(newId(), WorkshopTitle.of("   "), description()))
                .isInstanceOf(WorkshopDomainException.class);
    }

    // ----------------------------------------------------------------
    // schedule
    // ----------------------------------------------------------------

    @Test
    void schedule_fromDraft_assignsDataAndEmitsScheduled() {
        Workshop workshop = Workshop.create(newId(), title(), description());

        workshop.schedule(ROOM, START, END, CAPACITY);

        assertThat(workshop.state()).isEqualTo(WorkshopState.SCHEDULED);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.endTime()).isEqualTo(END);
        assertThat(workshop.capacity()).isEqualTo(CAPACITY);

        assertThat(workshop.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopScheduled.class);

        WorkshopScheduled event = (WorkshopScheduled) workshop.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.roomReference()).isEqualTo(ROOM);
        assertThat(event.startTime()).isEqualTo(START);
        assertThat(event.endTime()).isEqualTo(END);
        assertThat(event.capacity()).isEqualTo(CAPACITY);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void schedule_preventsOverlappingSchedulingByAllowingIt() {
        // ADR 0008: SCHEDULED is planning-only. Two workshops may share a room + overlap.
        // This test asserts the aggregate does NOT reject such a schedule (conflict is a publish-time concern).
        Workshop a = Workshop.create(newId(), title(), description());
        Workshop b = Workshop.create(newId(), WorkshopTitle.of("Other WS"), description());
        RoomReference sameRoom = RoomReference.of(ROOM.roomId(), "Room 201", "Floor 2");

        a.schedule(sameRoom, START, END, CAPACITY);
        b.schedule(sameRoom, START, END, CAPACITY);

        assertThat(a.state()).isEqualTo(WorkshopState.SCHEDULED);
        assertThat(b.state()).isEqualTo(WorkshopState.SCHEDULED);
    }

    @Test
    void schedule_rejectsNullRoom() {
        Workshop workshop = Workshop.create(newId(), title(), description());

        assertThatThrownBy(() -> workshop.schedule(null, START, END, CAPACITY))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("room must be assigned");
    }

    @Test
    void schedule_rejectsNullTime() {
        Workshop workshop = Workshop.create(newId(), title(), description());

        assertThatThrownBy(() -> workshop.schedule(ROOM, null, END, CAPACITY))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("start and end time");

        assertThatThrownBy(() -> workshop.schedule(ROOM, START, null, CAPACITY))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("start and end time");
    }

    @Test
    void schedule_rejectsEndNotAfterStart() {
        Workshop workshop = Workshop.create(newId(), title(), description());

        assertThatThrownBy(() -> workshop.schedule(ROOM, START, END_BEFORE_START, CAPACITY))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("after the start time");
    }

    @Test
    void schedule_rejectsNonDraftState() {
        Workshop workshop = Workshop.create(newId(), title(), description());
        workshop.schedule(ROOM, START, END, CAPACITY);

        // Already SCHEDULED — re-schedule (same or different data) must be rejected.
        assertThatThrownBy(() -> workshop.schedule(ROOM, START, END, CAPACITY))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.SCHEDULED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.DRAFT);
                });
    }

    @Test
    void capacityVo_rejectsNonPositive() {
        assertThatThrownBy(() -> WorkshopCapacity.of(0))
                .isInstanceOf(WorkshopDomainException.class);
        assertThatThrownBy(() -> WorkshopCapacity.of(-5))
                .isInstanceOf(WorkshopDomainException.class);
    }

    // ----------------------------------------------------------------
    // publish
    // ----------------------------------------------------------------

    @Test
    void publish_fromScheduled_reservesAndEmitsPublished() {
        Workshop workshop = Workshop.create(newId(), title(), description());
        workshop.schedule(ROOM, START, END, CAPACITY);

        workshop.publish();

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.recordedEvents())
                .hasSize(3)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopScheduled.class, WorkshopPublished.class);

        WorkshopPublished event = (WorkshopPublished) workshop.recordedEvents().get(2);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
    }

    @Test
    void publish_fromDraft_isRejected() {
        Workshop workshop = Workshop.create(newId(), title(), description());

        assertThatThrownBy(workshop::publish)
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.DRAFT);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.SCHEDULED);
                });
    }

    @Test
    void publish_doesNotRevalidateRoomTimeCapacity() {
        // The aggregate trusts schedule()'s invariants; publish() only transitions state.
        // (Global availability conflict is an Application-layer concern, not enforced here.)
        Workshop workshop = Workshop.create(newId(), title(), description());
        workshop.schedule(ROOM, START, END, CAPACITY);

        workshop.publish();

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.capacity()).isEqualTo(CAPACITY);
    }

    // ----------------------------------------------------------------
    // idempotency / timestamp
    // ----------------------------------------------------------------

    @Test
    void publish_twiceFromScheduled_isRejected() {
        Workshop workshop = Workshop.create(newId(), title(), description());
        workshop.schedule(ROOM, START, END, CAPACITY);
        workshop.publish();

        assertThatThrownBy(workshop::publish)
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void updatedAt_advancesOnTransition() {
        Workshop workshop = Workshop.create(newId(), title(), description());
        Instant created = workshop.updatedAt();

        workshop.schedule(ROOM, START, END, CAPACITY);
        assertThat(workshop.updatedAt()).isAfterOrEqualTo(created);

        Instant scheduledUpdate = workshop.updatedAt();
        workshop.publish();
        assertThat(workshop.updatedAt()).isAfterOrEqualTo(scheduledUpdate);
    }

    @Test
    void recordedEvents_areClearable() {
        Workshop workshop = Workshop.create(newId(), title(), description());
        assertThat(workshop.recordedEvents()).isNotEmpty();

        workshop.clearDomainEvents();
        assertThat(workshop.recordedEvents()).isEmpty();
    }
}
