package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.PlanningStatus;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPublishingException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.ChangeWorkshopRoomCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChangeWorkshopRoomCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-01T12:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID OLD_ROOM_ID = UUID.randomUUID();
    private static final UUID NEW_ROOM_ID = UUID.randomUUID();

    private static final RoomPlanningPermission ALLOWED_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.ALLOWED,
            null,
            new RoomPlanningData(NEW_ROOM_ID, "Room 302", new RoomPlanningData.Location("Building B", 3), 60)
    );

    private static final RoomPlanningPermission WARNING_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.WARNING,
            "Room is under maintenance",
            new RoomPlanningData(NEW_ROOM_ID, "Room 302", new RoomPlanningData.Location("Building B", 3), 60)
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

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ChangeWorkshopRoomCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChangeWorkshopRoomCommandHandler(
                workshopRepository, roomExposeApi, workshopDomainEventPublisher,
                new ScheduledWorkshopKicker(workshopRepository), fixedClock);
    }

    private Workshop createPublishedWorkshop() {
        Workshop workshop = Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                NOW);
        workshop.schedule(RoomReference.of(OLD_ROOM_ID, "Room 201", "Building A/2", 50), false, NOW);
        workshop.publish(NOW, 50);
        return workshop;
    }

    private Workshop createScheduledWorkshop(UUID id, UUID roomId, int roomCapacity, Instant start, Instant end) {
        Workshop workshop = Workshop.create(
                WorkshopId.of(id),
                WorkshopTitle.of("Other Workshop"),
                WorkshopDescription.of("Description"),
                start, end,
                WorkshopCapacity.of(20),
                NOW);
        workshop.schedule(RoomReference.of(roomId, "Room 302", "Building B/3", roomCapacity), false, NOW);
        return workshop;
    }

    @Nested
    class Success {

        @Test
        void changeRoom_movesWorkshopToNewRoomWhenAllowed() {
            Workshop workshop = createPublishedWorkshop();
            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.countOverlapping(NEW_ROOM_ID, START, END, WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(0);
            given(workshopRepository.loadByRoomId(NEW_ROOM_ID)).willReturn(List.of());

            ChangeWorkshopRoomCommand.Result result = handler.handle(
                    new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID));

            assertThat(result.roomId()).isEqualTo(NEW_ROOM_ID);
            assertThat(workshop.roomReference().roomId()).isEqualTo(NEW_ROOM_ID);
            assertThat(workshop.roomReference().roomNameSnapshot()).isEqualTo("Room 302");
            assertThat(workshop.state()).isEqualTo(WorkshopState.PUBLISHED);

            verify(workshopRepository).save(workshop);
            verify(workshopDomainEventPublisher).publish(any());
        }

        @Test
        void changeRoom_kicksOutOverlappingScheduledWorkshops() {
            Workshop workshop = createPublishedWorkshop();
            Workshop scheduled = createScheduledWorkshop(
                    UUID.randomUUID(), NEW_ROOM_ID, 60, START, END);

            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.countOverlapping(NEW_ROOM_ID, START, END, WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(0);
            given(workshopRepository.loadByRoomId(NEW_ROOM_ID)).willReturn(List.of(scheduled));

            ChangeWorkshopRoomCommand.Result result = handler.handle(
                    new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID));

            assertThat(result.roomId()).isEqualTo(NEW_ROOM_ID);
            assertThat(workshop.roomReference().roomId()).isEqualTo(NEW_ROOM_ID);
            // SCHEDULED workshop B was kicked back to DRAFT, freeing the new room.
            assertThat(scheduled.state()).isEqualTo(WorkshopState.DRAFT);
            assertThat(scheduled.roomReference()).isNull();

            verify(workshopRepository).save(workshop);
            verify(workshopRepository).save(scheduled);
            verify(workshopDomainEventPublisher).publish(any());
        }

        @Test
        void changeRoom_keepsNonOverlappingScheduledWorkshop() {
            Workshop workshop = createPublishedWorkshop();
            Workshop scheduled = createScheduledWorkshop(
                    UUID.randomUUID(), NEW_ROOM_ID, 60, LATER, LATER.plusSeconds(7200));

            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.countOverlapping(NEW_ROOM_ID, START, END, WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(0);
            given(workshopRepository.loadByRoomId(NEW_ROOM_ID)).willReturn(List.of(scheduled));

            handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID));

            assertThat(workshop.roomReference().roomId()).isEqualTo(NEW_ROOM_ID);
            assertThat(scheduled.state()).isEqualTo(WorkshopState.SCHEDULED);
            verify(workshopRepository, never()).save(scheduled);
        }
    }

    @Nested
    class RoomUnavailable {

        @Test
        void changeRoom_throwsWhenNewRoomIsUnderMaintenance() {
            Workshop workshop = createPublishedWorkshop();
            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(WARNING_PERMISSION));

            assertThatThrownBy(() -> handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID)))
                    .isInstanceOf(RoomNotAvailableForPublishingException.class)
                    .hasMessageContaining("Room is under maintenance");
        }

        @Test
        void changeRoom_throwsWhenNewRoomIsBlocked() {
            Workshop workshop = createPublishedWorkshop();
            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(BLOCKED_PERMISSION));

            assertThatThrownBy(() -> handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID)))
                    .isInstanceOf(RoomNotAvailableForPublishingException.class)
                    .hasMessageContaining("Room is deactivated");
        }

        @Test
        void changeRoom_throwsWhenNewRoomNotFound() {
            Workshop workshop = createPublishedWorkshop();
            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID)))
                    .isInstanceOf(ReferencedRoomNotFoundException.class);
        }
    }

    @Nested
    class Conflict {

        @Test
        void changeRoom_throwsWhenOverlappingPublishedInNewRoom() {
            Workshop workshop = createPublishedWorkshop();
            given(workshopRepository.loadByIdWithLock(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(NEW_ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));
            given(workshopRepository.countOverlapping(NEW_ROOM_ID, START, END, WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(1);

            assertThatThrownBy(() -> handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID)))
                    .isInstanceOf(RoomConflictException.class);
        }

        @Test
        void changeRoom_throwsWhenWorkshopNotFound() {
            given(workshopRepository.loadByIdWithLock(any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new ChangeWorkshopRoomCommand(WORKSHOP_ID, NEW_ROOM_ID)))
                    .isInstanceOf(WorkshopNotFoundException.class);
        }
    }
}
