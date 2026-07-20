package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.PlaceRoomUnderMaintenanceCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceRoomUnderMaintenanceCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    private PlaceRoomUnderMaintenanceCommandHandler handler() {
        return new PlaceRoomUnderMaintenanceCommandHandler(roomRepository);
    }

    private static Room existingRoom(RoomState state) {
        RoomLocation location = RoomLocation.of("F", 2);
        return Room.reconstruct(RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                location, 1, 50, state, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-03-15T00:00:00Z"));
    }

    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new PlaceRoomUnderMaintenanceCommand(id.value())))
                .isInstanceOf(RoomNotFoundException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void happyPath_fromActive_transitionsToMaintenance_emitsEvent() {
        Room room = existingRoom(RoomState.ACTIVE);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlaceRoomUnderMaintenanceCommand.Result response = handler().handle(
                new PlaceRoomUnderMaintenanceCommand(room.id().value()));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().state()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(captor.getValue().recordedEvents()).anyMatch(e -> e instanceof RoomStateChanged);
        assertThat(response.previousState()).isEqualTo(RoomState.ACTIVE);
        assertThat(response.newState()).isEqualTo(RoomState.MAINTENANCE);
    }

    @Test
    void deactivated_rejectsTransition_withoutSaving() {
        Room room = existingRoom(RoomState.DEACTIVATED);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> handler().handle(new PlaceRoomUnderMaintenanceCommand(room.id().value())))
                .isInstanceOf(IllegalRoomStateException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void idempotent_sameState_noSave_returnsExistingUpdatedAt() {
        Room room = existingRoom(RoomState.MAINTENANCE);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        PlaceRoomUnderMaintenanceCommand.Result response = handler().handle(
                new PlaceRoomUnderMaintenanceCommand(room.id().value()));

        assertThat(response.previousState()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(response.newState()).isEqualTo(RoomState.MAINTENANCE);
        assertThat(response.updatedAt()).isEqualTo(room.updatedAt()); // NOT Instant.now()
        verify(roomRepository, never()).save(any());
    }

    @Test
    void guardsRunInOrder_loadThenSave() {
        Room room = existingRoom(RoomState.ACTIVE);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new PlaceRoomUnderMaintenanceCommand(room.id().value()));

        var ordered = inOrder(roomRepository);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(roomRepository).save(any());
    }
}
