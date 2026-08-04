package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.exception.MaintenanceScheduleOverlapException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ScheduleRoomMaintenanceCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.MaintenanceScheduleRepository;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleRoomMaintenanceCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private MaintenanceScheduleRepository maintenanceScheduleRepository;

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private ScheduleRoomMaintenanceCommandHandler handler() {
        return new ScheduleRoomMaintenanceCommandHandler(
                roomRepository, maintenanceScheduleRepository, roomDomainEventPublisher, clock);
    }

    private static Room existingRoom(RoomState state) {
        return Room.reconstruct(RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), RoomCode.of(1), RoomCapacity.of(50), state,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-15T00:00:00Z"));
    }

    private static ScheduleRoomMaintenanceCommand validCommand(UUID roomId) {
        return new ScheduleRoomMaintenanceCommand(
                roomId,
                Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"),
                "Quarterly HVAC filter replacement and duct cleaning",
                "operator-1");
    }

    @Test
    void happyPath_schedules_and_persists() {
        Room room = existingRoom(RoomState.ACTIVE);
        when(roomRepository.loadByIdWithLock(room.id())).thenReturn(Optional.of(room));
        when(maintenanceScheduleRepository.loadOverlapping(eq(room.id().value()), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(maintenanceScheduleRepository.save(any(MaintenanceSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleRoomMaintenanceCommand.Result result = handler().handle(validCommand(room.id().value()));

        assertThat(result).isNotNull();
        assertThat(result.roomId()).isEqualTo(room.id().value());
        assertThat(result.startTime()).isEqualTo(Instant.parse("2026-08-01T08:00:00Z"));
        assertThat(result.endTime()).isEqualTo(Instant.parse("2026-08-01T12:00:00Z"));
        verify(maintenanceScheduleRepository).save(any(MaintenanceSchedule.class));
    }

    @Test
    void roomNotFound_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadByIdWithLock(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(validCommand(id.value())))
                .isInstanceOf(RoomNotFoundException.class);
        verify(maintenanceScheduleRepository, never()).save(any(MaintenanceSchedule.class));
    }

    @Test
    void roomDeactivated_throws() {
        Room room = existingRoom(RoomState.DEACTIVATED);
        when(roomRepository.loadByIdWithLock(room.id())).thenReturn(Optional.of(room));
        when(maintenanceScheduleRepository.loadOverlapping(eq(room.id().value()), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> handler().handle(validCommand(room.id().value())))
                .isInstanceOf(IllegalRoomStateException.class);
        verify(maintenanceScheduleRepository, never()).save(any(MaintenanceSchedule.class));
    }

    @Test
    void overlapDetected_throws() {
        Room room = existingRoom(RoomState.ACTIVE);
        when(roomRepository.loadByIdWithLock(room.id())).thenReturn(Optional.of(room));
        MaintenanceSchedule existing = MaintenanceSchedule.reconstruct(
                io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId.generate(),
                room.id(),
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T14:00:00Z"),
                "Existing maintenance schedule for testing overlap detection",
                "operator-2",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"));
        when(maintenanceScheduleRepository.loadOverlapping(eq(room.id().value()), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> handler().handle(validCommand(room.id().value())))
                .isInstanceOf(MaintenanceScheduleOverlapException.class);
        verify(maintenanceScheduleRepository, never()).save(any(MaintenanceSchedule.class));
    }

    @Test
    void idempotent_noOverlap_succeeds() {
        Room room = existingRoom(RoomState.ACTIVE);
        when(roomRepository.loadByIdWithLock(room.id())).thenReturn(Optional.of(room));
        when(maintenanceScheduleRepository.loadOverlapping(eq(room.id().value()), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(maintenanceScheduleRepository.save(any(MaintenanceSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleRoomMaintenanceCommand.Result result = handler().handle(validCommand(room.id().value()));

        ArgumentCaptor<MaintenanceSchedule> captor = ArgumentCaptor.forClass(MaintenanceSchedule.class);
        verify(maintenanceScheduleRepository).save(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo(room.id());
    }
}
