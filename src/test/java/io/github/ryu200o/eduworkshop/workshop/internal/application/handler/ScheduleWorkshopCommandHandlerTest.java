package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.PlanningStatus;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPlanningException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.ScheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleWorkshopCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T11:00:00Z");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final String ROOM_NAME = "Room 201";
    private static final String BUILDING = "Building A";
    private static final int FLOOR = 2;

    private static final RoomPlanningPermission ALLOWED_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.ALLOWED,
            "Room is active",
            new RoomPlanningData(ROOM_ID, ROOM_NAME, new RoomPlanningData.Location(BUILDING, FLOOR), 50)
    );

    private static final RoomPlanningPermission WARNING_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.WARNING,
            "Room is under maintenance",
            new RoomPlanningData(ROOM_ID, ROOM_NAME, new RoomPlanningData.Location(BUILDING, FLOOR), 50)
    );

    private static final RoomPlanningPermission BLOCKED_PERMISSION = new RoomPlanningPermission(
            PlanningStatus.BLOCKED,
            "Room is deactivated",
            new RoomPlanningData(ROOM_ID, ROOM_NAME, new RoomPlanningData.Location(BUILDING, FLOOR), 50)
    );

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RoomExposeAPI roomExposeApi;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ScheduleWorkshopCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScheduleWorkshopCommandHandler(workshopRepository, roomExposeApi, fixedClock);
    }

    private Workshop createDraftWorkshop() {
        return Workshop.create(
                WorkshopId.of(WORKSHOP_ID),
                WorkshopTitle.of("Test Workshop"),
                WorkshopDescription.of("Description"),
                START, END,
                WorkshopCapacity.of(30),
                NOW
        );
    }

    @Nested
    class RoomAllowed {

        @Test
        void schedulesSuccessfully() {
            var workshop = createDraftWorkshop();
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(ALLOWED_PERMISSION));

            var result = handler.handle(new ScheduleWorkshopCommand(WORKSHOP_ID, ROOM_ID));

            assertThat(result.id()).isEqualTo(WORKSHOP_ID);
            assertThat(result.roomId()).isEqualTo(ROOM_ID);
            assertThat(result.updatedAt()).isEqualTo(NOW);
            assertThat(result.hasRoomWarning()).isFalse();

            assertThat(workshop.state().name()).isEqualTo("SCHEDULED");
            assertThat(workshop.roomReference()).isNotNull();
            assertThat(workshop.roomReference().roomId()).isEqualTo(ROOM_ID);
            assertThat(workshop.roomReference().roomNameSnapshot()).isEqualTo(ROOM_NAME);
            assertThat(workshop.roomReference().roomLocationSnapshot()).isEqualTo(BUILDING + "/" + FLOOR);
            assertThat(workshop.roomReference().roomCapacitySnapshot()).isEqualTo(50);
            assertThat(workshop.hasRoomWarning()).isFalse();

            verify(workshopRepository).save(workshop);
        }

        @Test
        void persistsAndReturnsWarningFlag_whenRoomMaintenance() {
            var workshop = createDraftWorkshop();
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(WARNING_PERMISSION));

            var result = handler.handle(new ScheduleWorkshopCommand(WORKSHOP_ID, ROOM_ID));

            assertThat(result.hasRoomWarning()).isTrue();
            assertThat(workshop.hasRoomWarning()).isTrue();
            verify(workshopRepository).save(workshop);
        }
    }

    @Nested
    class RoomBlocked {

        @Test
        void throwsException_whenRoomIsDeactivated() {
            var workshop = createDraftWorkshop();
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(ROOM_ID))
                    .willReturn(Optional.of(BLOCKED_PERMISSION));

            assertThatThrownBy(() -> handler.handle(new ScheduleWorkshopCommand(WORKSHOP_ID, ROOM_ID)))
                    .isInstanceOf(RoomNotAvailableForPlanningException.class)
                    .hasMessageContaining("Room is deactivated");
        }
    }

    @Nested
    class RoomNotFound {

        @Test
        void throwsReferencedRoomNotFoundException() {
            var workshop = createDraftWorkshop();
            given(workshopRepository.loadById(WorkshopId.of(WORKSHOP_ID)))
                    .willReturn(Optional.of(workshop));
            given(roomExposeApi.checkPlanningPermission(ROOM_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new ScheduleWorkshopCommand(WORKSHOP_ID, ROOM_ID)))
                    .isInstanceOf(ReferencedRoomNotFoundException.class);
        }
    }

    @Nested
    class WorkshopNotFound {

        @Test
        void throwsWorkshopNotFoundException() {
            given(workshopRepository.loadById(any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new ScheduleWorkshopCommand(WORKSHOP_ID, ROOM_ID)))
                    .isInstanceOf(WorkshopNotFoundException.class);
        }
    }
}
