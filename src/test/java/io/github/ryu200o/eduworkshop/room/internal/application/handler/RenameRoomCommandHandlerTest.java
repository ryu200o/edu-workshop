package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameRoomCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    private RenameRoomCommandHandler handler() {
        return new RenameRoomCommandHandler(roomRepository);
    }

    private static Room existingRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        return Room.create(RoomName.of("F-201"), location, 1, 50);
    }

    // ── Step 1: load failure ──
    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id.value(), "F-202")))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 2: RAM guard (local invariant) blocks blank name (load happens first, no save/gate) ──
    @Test
    void ramGuard_rejectsBlankName_withoutTouchingPorts() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id.value(), "")))
                .isInstanceOf(RoomDomainException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    // ── Idempotency: same name ⇒ no gate, no save, returns current projection ──
    @Test
    void sameName_isIdempotent_noSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        RenameRoomCommand.Result response = handler().handle(new RenameRoomCommand(room.id().value(), "F-201"));

        assertThat(response.oldName()).isEqualTo("F-201");
        assertThat(response.newName()).isEqualTo("F-201");
        verify(roomRepository, never()).save(any());
    }

    // ── Step 4b: DB guard (name) blocks duplicate name at the same location, never persists ──
    @Test
    void dbGuard_rejectsDuplicateName_andDoesNotSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByName(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(room.id().value(), "LAB-101")))
                .isInstanceOf(DuplicateRoomException.class);

        verify(roomRepository).existsByName(any(), any());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 4: Happy path — passes guards, mutates, persists, returns projection ──
    @Test
    void happyPath_mutatesPersistsAndReturnsResponse() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RenameRoomCommand.Result response = handler().handle(new RenameRoomCommand(room.id().value(), "LAB-101"));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.name()).isEqualTo(RoomName.of("LAB-101"));
        assertThat(saved.recordedEvents()).anyMatch(e -> e instanceof RoomRenamedEvent);
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.oldName()).isEqualTo("F-201");
        assertThat(response.newName()).isEqualTo("LAB-101");
    }

    @Test
    void guardsRunInOrder_loadThenGateThenSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RenameRoomCommand(room.id().value(), "LAB-101"));

        var ordered = org.mockito.Mockito.inOrder(roomRepository);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(roomRepository).existsByName(any(), any());
        ordered.verify(roomRepository).save(any());
    }
}
