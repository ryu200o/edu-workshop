package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.PlanningStatus;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPublishingException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PublishWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopCapacityExceedsRoomException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublishWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    private static final RoomPlanningPermission ALLOWED_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.ALLOWED,
            null,
            new RoomPlanningData(ROOM_ID, "Room 201", new RoomPlanningData.Location("Building A", 2), 50)
    );

    private static final RoomPlanningPermission WARNING_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.WARNING,
            "Room is under maintenance",
            new RoomPlanningData(ROOM_ID, "Room 201", new RoomPlanningData.Location("Building A", 2), 50)
    );

    private static final RoomPlanningPermission BLOCKED_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.BLOCKED,
            "Room is deactivated",
            null
    );

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RoomExposeAPI roomExposeApi;

    @Mock
    private WorkshopDomainEventPublisher workshopDomainEventPublisher;

    @Captor
    private ArgumentCaptor<List<?>> eventsCaptor;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private PublishWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PublishWorkshopCommandHandler(
                workshopRepository, roomExposeApi, workshopDomainEventPublisher,
                new PlannedWorkshopKicker(workshopRepository), fixedClock);
    }

    private Workshop createPlannedWorkshop(int capacity) {
        return createPlannedWorkshop(capacity, WORKSHOP_ID, START, END);
    }

    private Workshop createPlannedWorkshop(int capacity, UUID workshopId) {
        return createPlannedWorkshop(capacity, workshopId, START, END);
    }

    private Workshop createPlannedWorkshop(int capacity, UUID workshopId, Instant start, Instant end) {
        Workshop workshop = Workshop.create(WorkshopId.of(workshopId), WorkshopTitle.of("Planned Workshop"), WorkshopDescription.of("Description"), start, end, start.minus(Duration.ofMinutes(15)), WorkshopCapacity.of(capacity), WorkshopLateThreshold.of(900), NOW);
        workshop.plan(
                RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50),
                false,
                workshop.occupancyStart(),
                NOW
        );
        return workshop;
    }

    @Nested
    class RoomAllowed {

        @Test
        void publishesSuccessfully() {
            Workshop workshop = createPlannedWorkshop(30);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop));

            PublishWorkshopCommand.Result result = handler.handle(
                    new PublishWorkshopCommand(WORKSHOP_ID));

            assertThat(result.id()).isEqualTo(WORKSHOP_ID);
            assertThat(result.updatedAt()).isEqualTo(NOW);
            assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);

            verify(workshopRepository).save(workshop);
            verify(workshopDomainEventPublisher).publish(any());
        }

        @Test
        void clearsWarningFlag_whenRoomIsNowAllowed() {
            Workshop workshop = createPlannedWorkshop(30);
            workshop.markMaintenanceWarning(NOW);
            assertThat(workshop.hasRoomWarning()).isTrue();

            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop));

            handler.handle(new PublishWorkshopCommand(WORKSHOP_ID));

            assertThat(workshop.hasRoomWarning()).isFalse();
        }

        @Test
        void rejectsWhenCapacityExceedsRoom() {
            Workshop workshop = createPlannedWorkshop(60);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop));

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(WorkshopCapacityExceedsRoomException.class);
        }
    }

    @Nested
    class KickOutPlanned {

        @Test
        void publish_kicksOutOverlappingPlannedWorkshops() {
            Workshop workshop = createPlannedWorkshop(30);
            Workshop planned = createPlannedWorkshop(20, UUID.randomUUID());
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop, planned));

            PublishWorkshopCommand.Result result = handler.handle(
                    new PublishWorkshopCommand(WORKSHOP_ID));

            assertThat(result.id()).isEqualTo(WORKSHOP_ID);
            assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
            // Overlapping PLANNED workshop was evicted back to DRAFT (keeps its room — UX upgrade).
            assertThat(planned.state()).isEqualTo(WorkshopState.DRAFT);
            assertThat(planned.roomReference()).isNotNull();
            assertThat(planned.roomReference().roomId()).isEqualTo(ROOM_ID);

            verify(workshopRepository).save(workshop);
            verify(workshopRepository).saveAll(any());
            verify(workshopDomainEventPublisher).publish(any());
        }

        @Test
        void publish_keepsNonOverlappingPlannedWorkshop() {
            Workshop workshop = createPlannedWorkshop(30);
            Workshop planned = createPlannedWorkshop(
                    20, UUID.randomUUID(),
                    Instant.parse("2026-09-01T12:00:00Z"),
                    Instant.parse("2026-09-01T14:00:00Z"));
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            // The non-overlapping workshop is not part of the locked overlapping set.
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop));

            handler.handle(new PublishWorkshopCommand(WORKSHOP_ID));

            assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);
            assertThat(planned.state()).isEqualTo(WorkshopState.PLANNED);
            verify(workshopRepository, never()).save(planned);
        }
    }

    @Nested
    class RoomBlocked {

        @Test
        void throwsException_whenRoomIsBlocked() {
            Workshop workshop = createPlannedWorkshop(30);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(BLOCKED_PERMISSION));

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(RoomNotAvailableForPublishingException.class)
                    .hasMessageContaining("Room is deactivated");
        }
    }

    @Nested
    class RoomWarning {

        @Test
        void throwsException_whenRoomIsUnderMaintenance() {
            Workshop workshop = createPlannedWorkshop(30);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(WARNING_PERMISSION));

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(RoomNotAvailableForPublishingException.class)
                    .hasMessageContaining("Room is under maintenance");
        }
    }

    @Nested
    class Overlap {

        @Test
        void throwsException_whenOverlappingPublishedWorkshopExists() {
            Workshop workshop = createPlannedWorkshop(30);
            Workshop otherPublished = createPlannedWorkshop(20, UUID.randomUUID());
            otherPublished.plan(
                    RoomReference.of(ROOM_ID, "Room 201", "Building A/2", 50),
                    false,
                    otherPublished.occupancyStart(),
                    NOW
            );
            otherPublished.publish(NOW, 50);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.loadPublishedAndPlannedOverlappingWithLock(eq(ROOM_ID), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(workshop, otherPublished));

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(RoomConflictException.class);
        }
    }

    @Nested
    class RoomNotFound {

        @Test
        void throwsReferencedRoomNotFoundException() {
            Workshop workshop = createPlannedWorkshop(30);
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.getPlanningPermission(ROOM_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(ReferencedRoomNotFoundException.class);
        }
    }

    @Nested
    class WorkshopNotFound {

        @Test
        void throwsWorkshopNotFoundException() {
            given(workshopRepository.loadById(any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new PublishWorkshopCommand(WORKSHOP_ID)))
                    .isInstanceOf(WorkshopNotFoundException.class);
        }
    }
}
