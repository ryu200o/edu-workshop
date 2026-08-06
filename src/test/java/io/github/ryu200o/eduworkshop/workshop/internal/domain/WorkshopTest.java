package io.github.ryu200o.eduworkshop.workshop.internal.domain;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopInformationUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPlanned;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopPublished;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRescheduled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopScheduleUpdated;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCapacityAdjusted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRoomEvicted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopStarted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCompleted;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopTimeRangeException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.RescheduleDeadlineExceededException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopAlreadyStartedException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityBelowRegistrationsException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCompletionNotDueException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopStartNotDueException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopTitleLockedException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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
    void plan_rejectsNonDraftOrPlannedState() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        workshop.publish(NOW, 50);

        // PUBLISHED is not a planning state — re-plan must be rejected.
        assertThatThrownBy(() -> workshop.plan(ROOM, false, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PUBLISHED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.DRAFT);
                });
    }

    @Test
    void plan_fromPlanned_replansRoomDirectly() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        RoomReference roomB = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);

        workshop.plan(roomB, true, NOW.plusSeconds(1));

        assertThat(workshop.state()).isEqualTo(WorkshopState.PLANNED);
        assertThat(workshop.roomReference()).isEqualTo(roomB);
        assertThat(workshop.hasRoomWarning()).isTrue();
        assertThat(workshop.recordedEvents())
                .hasSize(3)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class, WorkshopPlanned.class);
    }

    @Test
    void plan_fromPublished_isRejected() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.plan(ROOM, false, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PUBLISHED);
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
    // reschedule (post-publish)
    // ----------------------------------------------------------------

    @Test
    void reschedule_fromPublished_ok() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant rescheduleAt = NOW.plusSeconds(1);

        workshop.reschedule(newStart, newEnd, rescheduleAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
        assertThat(workshop.startTime()).isEqualTo(newStart);
        assertThat(workshop.endTime()).isEqualTo(newEnd);
        assertThat(workshop.updatedAt()).isEqualTo(rescheduleAt);

        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopRescheduled.class);
    }

    @Test
    void reschedule_emitsEventWithOldAndNewTimes() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant rescheduleAt = NOW.plusSeconds(1);

        workshop.reschedule(newStart, newEnd, rescheduleAt);

        WorkshopRescheduled event = (WorkshopRescheduled) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.oldStartTime()).isEqualTo(START);
        assertThat(event.oldEndTime()).isEqualTo(END);
        assertThat(event.newStartTime()).isEqualTo(newStart);
        assertThat(event.newEndTime()).isEqualTo(newEnd);
        assertThat(event.occurredAt()).isEqualTo(rescheduleAt);
    }

    @Test
    void reschedule_rejectsNonPublished() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() -> workshop.reschedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PLANNED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void reschedule_rejectsEndNotAfterStart() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.minusSeconds(1);

        assertThatThrownBy(() -> workshop.reschedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("after newStartTime");
    }

    @Test
    void reschedule_rejectsStartNotInFuture() {
        Workshop workshop = createPublished();
        Instant newStart = NOW.minusSeconds(1);
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() -> workshop.reschedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("in the future");
    }

    @Test
    void reschedule_rejectsAfterDeadline() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant within24h = START.minus(Duration.ofHours(12));

        assertThatThrownBy(() -> workshop.reschedule(newStart, newEnd, within24h))
                .isInstanceOf(RescheduleDeadlineExceededException.class)
                .satisfies(e -> {
                    RescheduleDeadlineExceededException ex = (RescheduleDeadlineExceededException) e;
                    assertThat(ex.getWorkshopId()).isEqualTo(workshop.id());
                    assertThat(ex.getDeadline()).isEqualTo(START.minus(Duration.ofHours(24)));
                    assertThat(ex.getAttemptedAt()).isEqualTo(within24h);
                });
    }

    @Test
    void reschedule_allowsExactlyAtDeadline() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant atDeadline = START.minus(Duration.ofHours(24));

        workshop.reschedule(newStart, newEnd, atDeadline);

        assertThat(workshop.startTime()).isEqualTo(newStart);
    }

    @Test
    void reschedule_rejectsNull() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(3));

        assertThatThrownBy(() -> workshop.reschedule(null, newStart, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workshop.reschedule(newStart, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workshop.reschedule(newStart, newStart, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // updateInformation
    // ----------------------------------------------------------------

    @Test
    void updateInformation_draft_allowsTitleAndDescriptionChange() {
        Workshop workshop = createDraft();
        WorkshopTitle newTitle = WorkshopTitle.of("Advanced Spring Boot");
        WorkshopDescription newDesc = WorkshopDescription.of("Deep dive into Modulith.");
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateInformation(newTitle, newDesc, 0, updatedAt);

        assertThat(workshop.title().value()).isEqualTo("Advanced Spring Boot");
        assertThat(workshop.description().value()).isEqualTo("Deep dive into Modulith.");
        assertThat(workshop.updatedAt()).isEqualTo(updatedAt);
        assertThat(workshop.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopInformationUpdated.class);

        WorkshopInformationUpdated event = (WorkshopInformationUpdated) workshop.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.newTitle()).isEqualTo("Advanced Spring Boot");
        assertThat(event.newDescription()).isEqualTo("Deep dive into Modulith.");
    }

    @Test
    void updateInformation_planned_allowsTitleAndDescriptionChange() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        WorkshopTitle newTitle = WorkshopTitle.of("Advanced Spring Boot");
        WorkshopDescription newDesc = WorkshopDescription.of("Deep dive into Modulith.");
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateInformation(newTitle, newDesc, 0, updatedAt);

        assertThat(workshop.title().value()).isEqualTo("Advanced Spring Boot");
        assertThat(workshop.description().value()).isEqualTo("Deep dive into Modulith.");
    }

    @Test
    void updateInformation_published_noRegistrations_allowsTitleChange() {
        Workshop workshop = createPublished();
        WorkshopTitle newTitle = WorkshopTitle.of("Advanced Spring Boot");
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateInformation(newTitle, description(), 0, updatedAt);

        assertThat(workshop.title().value()).isEqualTo("Advanced Spring Boot");
        assertThat(workshop.description().value()).isEqualTo("Hands-on intro to Spring Modulith.");
    }

    @Test
    void updateInformation_published_withRegistrations_locksTitle() {
        Workshop workshop = createPublished();
        WorkshopTitle newTitle = WorkshopTitle.of("Advanced Spring Boot");

        assertThatThrownBy(() -> workshop.updateInformation(newTitle, description(), 1, NOW))
                .isInstanceOf(WorkshopTitleLockedException.class)
                .satisfies(e -> {
                    WorkshopTitleLockedException ex = (WorkshopTitleLockedException) e;
                    assertThat(ex.getWorkshopId()).isEqualTo(workshop.id());
                    assertThat(ex.getActiveRegistrations()).isEqualTo(1);
                });
    }

    @Test
    void updateInformation_published_withRegistrations_allowsDescriptionChange() {
        Workshop workshop = createPublished();
        WorkshopDescription newDesc = WorkshopDescription.of("Updated syllabus and materials.");
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateInformation(title(), newDesc, 5, updatedAt);

        assertThat(workshop.description().value()).isEqualTo("Updated syllabus and materials.");
        assertThat(workshop.title().value()).isEqualTo("Spring Boot Workshop");
    }

    @Test
    void updateInformation_cancelled_isRejected() {
        Workshop workshop = createPublished();
        workshop.cancel(NOW.plusSeconds(1));

        assertThatThrownBy(() -> workshop.updateInformation(title(), description(), 0, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    // ----------------------------------------------------------------
    // updateSchedule
    // ----------------------------------------------------------------

    @Test
    void updateSchedule_draft_validRange_updatesTimes() {
        Workshop workshop = createDraft();
        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateSchedule(newStart, newEnd, updatedAt);

        assertThat(workshop.startTime()).isEqualTo(newStart);
        assertThat(workshop.endTime()).isEqualTo(newEnd);
        assertThat(workshop.updatedAt()).isEqualTo(updatedAt);
        assertThat(workshop.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopScheduleUpdated.class);

        WorkshopScheduleUpdated event = (WorkshopScheduleUpdated) workshop.recordedEvents().get(1);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.newStartTime()).isEqualTo(newStart);
        assertThat(event.newEndTime()).isEqualTo(newEnd);
    }

    @Test
    void updateSchedule_planned_validRange_updatesTimes() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);
        Instant updatedAt = NOW.plusSeconds(1);

        workshop.updateSchedule(newStart, newEnd, updatedAt);

        assertThat(workshop.startTime()).isEqualTo(newStart);
        assertThat(workshop.endTime()).isEqualTo(newEnd);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
    }

    @Test
    void updateSchedule_published_isRejected() {
        Workshop workshop = createPublished();
        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() -> workshop.updateSchedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void updateSchedule_cancelled_isRejected() {
        Workshop workshop = createPublished();
        workshop.cancel(NOW.plusSeconds(1));
        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() -> workshop.updateSchedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void updateSchedule_rejectsEndNotAfterStart() {
        Workshop workshop = createDraft();
        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.minusSeconds(1);

        assertThatThrownBy(() -> workshop.updateSchedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("after newStartTime");
    }

    @Test
    void updateSchedule_rejectsStartNotInFuture() {
        Workshop workshop = createDraft();
        Instant newStart = NOW.minusSeconds(1);
        Instant newEnd = newStart.plusSeconds(7200);

        assertThatThrownBy(() -> workshop.updateSchedule(newStart, newEnd, NOW))
                .isInstanceOf(InvalidWorkshopTimeRangeException.class)
                .hasMessageContaining("in the future");
    }

    // ----------------------------------------------------------------
    // evictPlanningOnConflict
    // ----------------------------------------------------------------

    @Test
    void evictPlanningOnConflict_keepsRoomAndWarningAndTimes() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, true, NOW);
        Instant evictAt = NOW.plusSeconds(1);

        workshop.evictPlanningOnConflict(evictAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(workshop.roomReference()).isEqualTo(ROOM);
        assertThat(workshop.hasRoomWarning()).isTrue();
        assertThat(workshop.startTime()).isEqualTo(START);
        assertThat(workshop.endTime()).isEqualTo(END);
        assertThat(workshop.updatedAt()).isEqualTo(evictAt);
        assertThat(workshop.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class);
    }

    @Test
    void evictPlanningOnConflict_rejectsNonPlanned() {
        Workshop workshop = createDraft();

        assertThatThrownBy(() -> workshop.evictPlanningOnConflict(NOW))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void evictedWorkshop_canBeReplanned() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);
        workshop.evictPlanningOnConflict(NOW.plusSeconds(1));

        RoomReference roomB = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);
        workshop.plan(roomB, false, NOW.plusSeconds(2));

        assertThat(workshop.state()).isEqualTo(WorkshopState.PLANNED);
        assertThat(workshop.roomReference()).isEqualTo(roomB);
    }

    @Test
    void returnToDraft_stillClearsRoomAndWarning() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, true, NOW);

        workshop.returnToDraft(NOW.plusSeconds(1));

        assertThat(workshop.state()).isEqualTo(WorkshopState.DRAFT);
        assertThat(workshop.roomReference()).isNull();
        assertThat(workshop.hasRoomWarning()).isFalse();
    }

    // ----------------------------------------------------------------
    // markRoomEvicted (Titik 2)
    // ----------------------------------------------------------------

    @Test
    void markRoomEvicted_publishedWorkshop_setsFlagAndEmitsEvent() {
        Workshop workshop = createPublished();
        Instant evictedAt = NOW.plusSeconds(1);

        workshop.markRoomEvicted(evictedAt);

        assertThat(workshop.isRoomEvicted()).isTrue();
        assertThat(workshop.roomEvictedAt()).isEqualTo(evictedAt);
        assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
        assertThat(workshop.updatedAt()).isEqualTo(evictedAt);

        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopRoomEvicted.class);

        WorkshopRoomEvicted event = (WorkshopRoomEvicted) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.roomId()).isEqualTo(ROOM.roomId());
        assertThat(event.occurredAt()).isEqualTo(evictedAt);
    }

    @Test
    void markRoomEvicted_alreadyEvicted_isIdempotent() {
        Workshop workshop = createPublished();
        workshop.markRoomEvicted(NOW.plusSeconds(1));
        workshop.clearDomainEvents();

        workshop.markRoomEvicted(NOW.plusSeconds(2));

        assertThat(workshop.isRoomEvicted()).isTrue();
        assertThat(workshop.roomEvictedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(workshop.recordedEvents()).isEmpty();
    }

    @Test
    void markRoomEvicted_nonPublished_isRejected() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);

        assertThatThrownBy(() -> workshop.markRoomEvicted(NOW))
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    @Test
    void changeRoom_whenEvicted_clearsEvictionFlag() {
        Workshop workshop = createPublished();
        workshop.markRoomEvicted(NOW.plusSeconds(1));
        RoomReference newRoom = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);

        workshop.changeRoom(newRoom, NOW.plusSeconds(2));

        assertThat(workshop.isRoomEvicted()).isFalse();
        assertThat(workshop.roomEvictedAt()).isNull();
        assertThat(workshop.roomReference()).isEqualTo(newRoom);
    }

    @Test
    void reschedule_whenEvicted_clearsEvictionFlag() {
        Workshop workshop = createPublished();
        workshop.markRoomEvicted(NOW.plusSeconds(1));
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);

        workshop.reschedule(newStart, newEnd, NOW.plusSeconds(2));

        assertThat(workshop.isRoomEvicted()).isFalse();
        assertThat(workshop.roomEvictedAt()).isNull();
        assertThat(workshop.startTime()).isEqualTo(newStart);
    }

    // ----------------------------------------------------------------
    // start / complete (Epic 1 — workshop lifecycle completion)
    // ----------------------------------------------------------------

    @Test
    void start_published_becomesInProgressAndEmitsStarted() {
        Workshop workshop = createPublished();
        Instant startedAt = START;

        workshop.start(startedAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.IN_PROGRESS);
        assertThat(workshop.updatedAt()).isEqualTo(startedAt);
        // room present at PUBLISHED (invariant); event carries roomId
        assertThat(workshop.recordedEvents())
                .hasSize(4)
                .hasExactlyElementsOfTypes(WorkshopCreated.class, WorkshopPlanned.class,
                        WorkshopPublished.class, WorkshopStarted.class);

        WorkshopStarted event = (WorkshopStarted) workshop.recordedEvents().get(3);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.roomId()).isEqualTo(ROOM.roomId());
        assertThat(event.occurredAt()).isEqualTo(startedAt);
    }

    @Test
    void start_atExactStartTime_isAllowed() {
        Workshop workshop = createPublished();

        workshop.start(START);

        assertThat(workshop.state()).isEqualTo(WorkshopState.IN_PROGRESS);
    }

    @Test
    void start_beforeStartTime_throwsStartNotDue() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.start(START.minusSeconds(1)))
                .isInstanceOf(WorkshopStartNotDueException.class)
                .satisfies(e -> {
                    WorkshopStartNotDueException ex = (WorkshopStartNotDueException) e;
                    assertThat(ex.workshopId()).isEqualTo(workshop.id());
                    assertThat(ex.startTime()).isEqualTo(START);
                });
    }

    @Test
    void start_fromNonPublished_throwsInvalidState() {
        Workshop workshop = createDraft();
        workshop.plan(ROOM, false, NOW);  // PLANNED

        assertThatThrownBy(() -> workshop.start(START))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PLANNED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.PUBLISHED);
                });
    }

    @Test
    void start_rejectsNullNow() {
        Workshop workshop = createPublished();

        assertThatThrownBy(() -> workshop.start(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void complete_inProgress_becomesCompletedAndEmitsCompleted() {
        Workshop workshop = createPublished();
        workshop.start(START);
        workshop.clearDomainEvents();
        Instant completedAt = END;

        workshop.complete(completedAt);

        assertThat(workshop.state()).isEqualTo(WorkshopState.COMPLETED);
        assertThat(workshop.updatedAt()).isEqualTo(completedAt);
        assertThat(workshop.recordedEvents())
                .hasSize(1)
                .hasOnlyElementsOfType(WorkshopCompleted.class);

        WorkshopCompleted event = (WorkshopCompleted) workshop.recordedEvents().get(0);
        assertThat(event.workshopId()).isEqualTo(workshop.id());
        assertThat(event.occurredAt()).isEqualTo(completedAt);
    }

    @Test
    void complete_atExactEndTime_isAllowed() {
        Workshop workshop = createPublished();
        workshop.start(START);
        workshop.clearDomainEvents();

        workshop.complete(END);

        assertThat(workshop.state()).isEqualTo(WorkshopState.COMPLETED);
    }

    @Test
    void complete_beforeEndTime_throwsCompletionNotDue() {
        Workshop workshop = createPublished();
        workshop.start(START);
        workshop.clearDomainEvents();

        assertThatThrownBy(() -> workshop.complete(END.minusSeconds(1)))
                .isInstanceOf(WorkshopCompletionNotDueException.class)
                .satisfies(e -> {
                    WorkshopCompletionNotDueException ex = (WorkshopCompletionNotDueException) e;
                    assertThat(ex.workshopId()).isEqualTo(workshop.id());
                    assertThat(ex.endTime()).isEqualTo(END);
                });
    }

    @Test
    void complete_fromNonInProgress_throwsInvalidState() {
        Workshop workshop = createPublished();  // still PUBLISHED

        assertThatThrownBy(() -> workshop.complete(END))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.PUBLISHED);
                    assertThat(ex.getAttemptedState()).isEqualTo(WorkshopState.IN_PROGRESS);
                });
    }

    @Test
    void complete_rejectsNullNow() {
        Workshop workshop = createPublished();
        workshop.start(START);
        workshop.clearDomainEvents();

        assertThatThrownBy(() -> workshop.complete(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_twice_isRejected() {
        Workshop workshop = createPublished();
        workshop.start(START);

        assertThatThrownBy(() -> workshop.start(START))  // second start
                .isInstanceOf(InvalidWorkshopStateException.class);
    }

    // Frozen matrix: post-start/post-complete all mutations are locked.
    @Test
    void updateInformation_afterStart_isRejected() {
        Workshop workshop = createPublished();
        workshop.start(START);

        assertThatThrownBy(() -> workshop.updateInformation(title(), description(), 0, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.IN_PROGRESS);
                });
    }

    @Test
    void updateInformation_afterComplete_isRejected() {
        Workshop workshop = createPublished();
        workshop.start(START);
        workshop.complete(END);

        assertThatThrownBy(() -> workshop.updateInformation(title(), description(), 0, NOW))
                .isInstanceOf(InvalidWorkshopStateException.class)
                .satisfies(e -> {
                    InvalidWorkshopStateException ex = (InvalidWorkshopStateException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(WorkshopState.COMPLETED);
                });
    }

    @Test
    void frozenMatrix_publishedMutations_rejectedAfterStartOrComplete() {
        // reschedule / changeRoom / adjustCapacity / cancel all requireState(PUBLISHED)
        // so once IN_PROGRESS or COMPLETED they must be locked.
        Workshop started_ = createPublished();
        started_.start(START);
        Workshop completed = createPublished();
        completed.start(START);
        completed.complete(END);
        RoomReference newRoom = RoomReference.of(UUID.randomUUID(), "Room 301", "Floor 3", 50);

        for (Workshop w : java.util.List.of(started_, completed)) {
            assertThatThrownBy(() -> w.reschedule(START.plus(Duration.ofDays(3)),
                    START.plus(Duration.ofDays(3)).plusSeconds(7200), NOW))
                    .isInstanceOf(InvalidWorkshopStateException.class);
            assertThatThrownBy(() -> w.changeRoom(newRoom, NOW))
                    .isInstanceOf(InvalidWorkshopStateException.class);
            assertThatThrownBy(() -> w.adjustCapacity(WorkshopCapacity.of(40), 0, NOW))
                    .isInstanceOf(InvalidWorkshopStateException.class);
            assertThatThrownBy(() -> w.cancel(NOW))
                    .isInstanceOf(InvalidWorkshopStateException.class);
            assertThatThrownBy(() -> w.updateSchedule(START.plus(Duration.ofDays(3)),
                    START.plus(Duration.ofDays(3)).plusSeconds(7200), NOW))
                    .isInstanceOf(InvalidWorkshopStateException.class);
        }
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
