package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData.Location;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopPersistenceException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.ScheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;

import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleWorkshopCommandHandlerTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RoomExposeAPI roomExposeApi;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
    }

    private ScheduleWorkshopCommandHandler handler() {
        return new ScheduleWorkshopCommandHandler(workshopRepository, roomExposeApi, clock);
    }

    private static Workshop aDraftWorkshop() {
        Instant now = Instant.parse("2026-07-01T00:00:00Z");
        return Workshop.create(
                WorkshopId.generate(),
                WorkshopTitle.of("AI Workshop"),
                WorkshopDescription.of("Intro to AI"),
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"),
                WorkshopCapacity.of(25),
                now
        );
    }

    private static RoomPlanningPermission aPermission(UUID roomId) {
        return new RoomPlanningPermission(
                RoomPlanningPermission.PlanningStatus.ALLOWED,
                null,
                new RoomPlanningData(
                        roomId,
                        "F.0201",
                        new Location("F", 2),
                        50
                ));
    }

    @Test
    void happyPath_schedulesWorkshop() {
        Workshop workshop = aDraftWorkshop();
        UUID roomId = UUID.randomUUID();
        RoomPlanningPermission permission = aPermission(roomId);

        when(workshopRepository.loadById(workshop.id())).thenReturn(Optional.of(workshop));
        when(roomExposeApi.checkPlanningPermission(roomId)).thenReturn(Optional.of(permission));
        when(workshopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleWorkshopCommand.Result result = handler().handle(
                new ScheduleWorkshopCommand(workshop.id().value(), roomId));

        assertThat(result.id()).isEqualTo(workshop.id().value());
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.updatedAt()).isEqualTo(Instant.now(clock));
        assertThat(workshop.state()).isEqualTo(WorkshopState.SCHEDULED);
        assertThat(workshop.roomReference()).isNotNull();
        assertThat(workshop.roomReference().roomId()).isEqualTo(roomId);
        assertThat(workshop.roomReference().roomNameSnapshot()).isEqualTo("F.0201");
        assertThat(workshop.roomReference().roomLocationSnapshot()).isEqualTo("F/2");

        verify(workshopRepository).save(workshop);
    }

    @Test
    void workshopNotFound_throwsWorkshopNotFoundException() {
        UUID workshopId = UUID.randomUUID();
        when(workshopRepository.loadById(WorkshopId.of(workshopId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new ScheduleWorkshopCommand(workshopId, UUID.randomUUID())))
                .isInstanceOf(WorkshopNotFoundException.class);

        verifyNoInteractions(roomExposeApi);
    }

    @Test
    void roomNotFound_throwsReferencedRoomNotFoundException() {
        Workshop workshop = aDraftWorkshop();
        UUID roomId = UUID.randomUUID();

        when(workshopRepository.loadById(workshop.id())).thenReturn(Optional.of(workshop));
        when(roomExposeApi.checkPlanningPermission(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new ScheduleWorkshopCommand(workshop.id().value(), roomId)))
                .isInstanceOf(ReferencedRoomNotFoundException.class);

        verify(workshopRepository).loadById(workshop.id());
        verify(roomExposeApi).checkPlanningPermission(roomId);
    }

    @Test
    void invalidState_throwsInvalidWorkshopStateException() {
        Workshop workshop = aDraftWorkshop();
        UUID roomId = UUID.randomUUID();
        RoomPlanningPermission permission = aPermission(roomId);
        workshop.schedule(
                RoomReference.of(roomId, "F.0201", "F/2"),
                Instant.parse("2026-07-15T00:00:00Z"));

        when(workshopRepository.loadById(workshop.id())).thenReturn(Optional.of(workshop));
        when(roomExposeApi.checkPlanningPermission(roomId)).thenReturn(Optional.of(permission));

        assertThatThrownBy(() -> handler().handle(
                new ScheduleWorkshopCommand(workshop.id().value(), roomId)))
                .isInstanceOf(InvalidWorkshopStateException.class);

        verify(workshopRepository).loadById(workshop.id());
        verify(roomExposeApi).checkPlanningPermission(roomId);
    }

    @Test
    void persistenceFailure_throwsWorkshopPersistenceException() {
        Workshop workshop = aDraftWorkshop();
        UUID roomId = UUID.randomUUID();
        RoomPlanningPermission permission = aPermission(roomId);

        when(workshopRepository.loadById(workshop.id())).thenReturn(Optional.of(workshop));
        when(roomExposeApi.checkPlanningPermission(roomId)).thenReturn(Optional.of(permission));
        when(workshopRepository.save(any())).thenThrow(new WorkshopPersistenceException("DB failure", new RuntimeException()));

        assertThatThrownBy(() -> handler().handle(
                new ScheduleWorkshopCommand(workshop.id().value(), roomId)))
                .isInstanceOf(WorkshopPersistenceException.class)
                .hasMessageContaining("DB failure");
    }
}
