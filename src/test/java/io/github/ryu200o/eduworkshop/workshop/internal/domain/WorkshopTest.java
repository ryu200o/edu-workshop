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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPlanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCapacityAdjusted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkshopTest {

    private static final Instant NOW = Instant.now();
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant END_BEFORE_START = Instant.parse("2026-09-01T08:00:00Z");
    private static final RoomReference ROOM = RoomReference.of(UUID.randomUUID(), "Room 201", "Floor 2", 50);
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

    private Workshop createDraft() {
        return Workshop.create(newId(), title(), description(), START, END, CAPACITY, NOW);
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Test
    void create_producesDraftWithCreatedEvent() {
        WorkshopId id = newId();
        Workshop workshop = Workshop.create(id, title(), description(), START, END, CAPACITY, NOW);

        assertThat(workshop.id()).isEqualTo(id);
        assertThat(workshop.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(workshop.title().value()).isEqualTo("Spring Boot Workshop");
        assertThat(workshop.description().value()).isEqualTo("Hands-on intro to Spring Modulith.");
        assertThat(workshop.roomReference()).isNull();
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.endTime()).isEqualTo(END);
        assertThat(workshop.capacity()).isEqualTo(CAPACITY);

        assertThat(workshop.recordedEvents())
                .hasSize(1)
                .hasOnlyElementsOfType(WorkshopCreated.class);

        WorkshopCreated event = (WorkshopCreated) workshop.recordedEvents().get(0);
        assertThat(event.startTime()).isEqualTo(START);
        assertThat(event.endTime()).isEqualTo(END);
        assertThat(event.capacity()).isEqualTo(CAPACITY);
    }

    @Test
    void create_rejectsBlankTitle() {
        assertThatThrownBy(() -> Workshop.create(newId(), WorkshopTitle.of("   "), description(), START, END, CAPACITY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsEndNotAfterStart() {
        assertThatThrownBy(() -> Workshop.create(newId(), title(), description(), START, END_BEFORE_START, CAPACITY, NOW))
                .isInstanceOf(WorkshopDomainException.class)
                .hasMessageContaining("after startTime");
    }

    // ----------------------------------------------------------------
    // plan
    // ----------------------------------------------------------------

    @Test
    void plan_fromDraft_assignsRoomAndEmitsPlanned() {
        Workshop workshop = createDraft();

        workshop.plan(ROOM, false, NOW);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PLANNED);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);

        // Time/capacity unchanged from creation
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.endTime()).isEqualTo(END);
        assertThat(workshop.capacity()).isEqualTo(CAPACITY);

        assertThat(workshop.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class);

        WorkshopPlanned event = (WorkshopPlanned) workshop.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.roomReference()).isEqualTo(ROOM);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void plan_allowsOverlappingPlanning() {
        // ADR 0008: PLANNED is planning-only. Two workshops may share a room + overlap.
        // This test asserts the aggregate does NOT reject such a plan (conflict is a publish-time concern).
        Workshop a = createDraft();
        Workshop b = Workshop.create(newId(), WorkshopTitle.of("Other WS"), description(), START, END, CAPACITY, NOW);
        RoomReference sameRoom = RoomReference.of(ROOM.roomId(), "Room 201", "Floor 2", 50);

        a.plan(sameRoom, false, NOW);
        b.plan(sameRoom, false, NOW);

        assertThat(a.state()).isEqualTo(WorkshopState.PLANNED);
        assertThat(b.state()).isEqualTo(WorkshopState.PLANNED);
    }

    @Test
    void plan_rejectsNullRoom() {
        Workshop workshop = createDraft();

        assertThatThrownBy(() -> workshop.plan(null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("room must be assigned");
    }

    @Test
    void plan_rejectsNonDraftState() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        // Already PLANNED — re-plan must be rejected.
        assertThatThrownBy(() -> workshop.plan(ROOM, false, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PLANNED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.DRAFT);
                });
    }

    @Test
    void capacityVo_rejectsNonPositive() {
        assertThatThrownBy(() -> WorkshopCapacity.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkshopCapacity.of(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // publish
    // ----------------------------------------------------------------

    @Test
    void publish_fromPlanned_reservesAndEmitsPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        workshop.publish(NOW, 50);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.recordedEvents())
                .hasSize(3)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class, WorkshopPublished.class);

        WorkshopPublished event = (WorkshopPublished) workshop.recordedEvents().get(2);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
    }

    @Test
    void publish_whenCapacityExceedsRoom_isRejected() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        assertThatThrownBy(() -> workshop.publish(NOW, 20))
                .isInstanceOf(WorkshopCapacityExceedsRoomException.class);
    }

    @Test
    void publish_fromDraft_isRejected() {
        Workshop workshop = createDraft();

        assertThatThrownBy(() -> workshop.publish(NOW, 50))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.DRAFT);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.PLANNED);
                });
    }

    @Test
    void publish_doesNotRevalidateRoomTimeCapacity() {
        // The aggregate trusts plan()'s invariants; publish() only transitions state.
        // (Global availability conflict is an Application-layer concern, not enforced here.)
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        workshop.publish(NOW, 50);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.capacity()).isEqualTo(CAPACITY);
    }

    // ----------------------------------------------------------------
    // changeRoom (post-publish)
    // ----------------------------------------------------------------

    private Workshop createPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    @Test
    void changeRoom_successfulWhenPublishedAndCapacityFits() {
        Workshop workshop = createPublished();
        RoomReference newRoom = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);
        Instant changedAt = NOW.plusSeconds(1);

        workshop.changeRoom(newRoom, changedAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.roomReference()).isEqualTo(newRoom);
        assertThat(workshop.updatedAt()).isEqualTo(changedAt);

        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopRoomChanged.class);

        WorkshopRoomChanged event = (WorkshopRoomChanged) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.roomReference()).isEqualTo(newRoom);
    }

    @Test
    void changeRoom_throwsExceptionWhenNotPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        RoomReference newRoom = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);

        assertThatThrownBy(() -> workshop.changeRoom(newRoom, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PLANNED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void changeRoom_throwsExceptionWhenNewRoomCapacityIsSmallerThanWorkshopCapacity() {
        Workshop workshop = createPublished();
        RoomReference tooSmallRoom = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 10);

        assertThatThrownBy(() -> workshop.changeRoom(tooSmallRoom, NOW))
                .isInstanceOf(WorkshopCapacityExceedsRoomException.class);
    }

    // ----------------------------------------------------------------
    // adjustCapacity (post-publish)
    // ----------------------------------------------------------------

    @Test
    void adjustCapacity_successfulWhenValid() {
        Workshop workshop = createPublished();
        WorkshopCapacity newCapacity = WorkshopCapacity.of(40);
        Instant adjustedAt = NOW.plusSeconds(1);

        workshop.adjustCapacity(newCapacity, 25, adjustedAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.capacity()).isEqualTo(newCapacity);
        assertThat(workshop.updatedAt()).isEqualTo(adjustedAt);

        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopCapacityAdjusted.class);

        WorkshopCapacityAdjusted event = (WorkshopCapacityAdjusted) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.newCapacity()).isEqualTo(newCapacity);
    }

    @Test
    void adjustCapacity_throwsExceptionWhenNewCapacityLessThanActiveRegistrations() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.adjustCapacity(WorkshopCapacity.of(20), 25, NOW))
                .isInstanceOf(WorkshopCapacityBelowRegistrationsException.class);
    }

    @Test
    void adjustCapacity_throwsExceptionWhenNewCapacityExceedsRoomCapacity() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.adjustCapacity(WorkshopCapacity.of(80), 25, NOW))
                .isInstanceOf(WorkshopCapacityExceedsRoomException.class);
    }

    @Test
    void adjustCapacity_throwsExceptionWhenNotPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        assertThatThrownBy(() -> workshop.adjustCapacity(WorkshopCapacity.of(40), 25, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    // ----------------------------------------------------------------
    // cancel (post-publish)
    // ----------------------------------------------------------------

    @Test
    void cancel_successfulWhenBeforeStartTime() {
        Workshop workshop = createPublished();
        Instant cancelAt = START.minusSeconds(3600);

        workshop.cancel(cancelAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.CANCELLED);
        assertThat(workshop.updatedAt()).isEqualTo(cancelAt);

        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopCancelled.class);

        WorkshopCancelled event = (WorkshopCancelled) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
    }

    @Test
    void cancel_throwsExceptionWhenNowIsAfterOrEqualsStartTime() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.cancel(START))
                .isInstanceOf(WorkshopAlreadyStartedException.class);
        assertThatThrownBy(() -> workshop.cancel(START.plusSeconds(1)))
                .isInstanceOf(WorkshopAlreadyStartedException.class)
                .satisfies(e -> {
                    WorkshopAlreadyStartedException ex = (WorkshopAlreadyStartedException) e;
                    assertThat(ex.workshopId()).isEqualTo(workshop.id());
                    assertThat(ex.startTime()).isEqualTo(START);
                });
    }

    @Test
    void cancel_throwsExceptionWhenNotPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        assertThatThrownBy(() -> workshop.cancel(NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PLANNED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void cancel_rejectsNullNow() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.cancel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // idempotency / timestamp
    // ----------------------------------------------------------------

    @Test
    void publish_twiceFromPlanned_isRejected() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        workshop.publish(NOW, 50);

        assertThatThrownBy(() -> workshop.publish(NOW, 50))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void updatedAt_advancesOnTransition() {
        Workshop workshop = createDraft();
        Instant created = workshop.updatedAt();

        workshop.plan(ROOM, false, NOW);
        assertThat(workshop.updatedAt()).isAfterOrEqualTo(created);

        Instant plannedUpdate = workshop.updatedAt();
        workshop.publish(NOW, 50);
        assertThat(workshop.updatedAt()).isAfterOrEqualTo(plannedUpdate);
    }

    @Test
    void recordedEvents_areClearable() {
        Workshop workshop = createDraft();
        assertThat(workshop.recordedEvents()).isNotEmpty();

        workshop.clearDomainEvents();
        assertThat(workshop.recordedEvents()).isEmpty();
    }
}
