package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RoomRenamedResult;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameRoomCommandHandlerTest {

    @Mock
    private RoomStateGateway roomStateGateway;

    @Mock
    private RoomExistencePort roomExistencePort;

    private RenameRoomCommandHandler handler() {
        return new RenameRoomCommandHandler(roomStateGateway, roomExistencePort);
    }

    private static Room existingRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        return Room.create(RoomName.of(location, "01"), location, 50);
    }

    // ── Step 1: load failure ──
    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        UUID id = UUID.randomUUID();
        when(roomStateGateway.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id, "02")))
                .isInstanceOf(RoomNotFoundException.class);

        verifyNoInteractions(roomExistencePort);
        verify(roomStateGateway, never()).save(any());
    }

    // ── Step 2: RAM guard (local invariant) blocks malformed code BEFORE any DB/port call ──
    @Test
    void ramGuard_rejectsInvalidCode_withoutTouchingPorts() {
        UUID id = UUID.randomUUID();
        when(roomStateGateway.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id, "")))
                .isInstanceOf(RoomDomainException.class);

        verify(roomStateGateway, never()).save(any());
        verifyNoInteractions(roomExistencePort);
    }

    // ── Step 4: DB guard (global invariant) blocks duplicates, never persists ──
    @Test
    void dbGuard_rejectsDuplicate_andDoesNotSave() {
        Room room = existingRoom();
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomExistencePort.existsByBuildingAndFloorAndCode(any(), anyInt(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(room.id(), "02")))
                .isInstanceOf(DuplicateRoomException.class);

        verify(roomExistencePort).existsByBuildingAndFloorAndCode(any(), anyInt(), any());
        verify(roomStateGateway, never()).save(any());
    }

    // ── Idempotency: same code ⇒ no gate, no save, returns current projection ──
    @Test
    void sameCode_isIdempotent_noGateNoSave() {
        Room room = existingRoom();
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));

        RoomRenamedResult response = handler().handle(new RenameRoomCommand(room.id(), "01"));

        assertThat(response.name()).isEqualTo(room.name().asString());
        assertThat(response.oldCode()).isEqualTo("01");
        assertThat(response.newCode()).isEqualTo("01");
        verify(roomExistencePort, never()).existsByBuildingAndFloorAndCode(any(), anyInt(), any());
        verify(roomStateGateway, never()).save(any());
    }

    // ── Step 5: Happy path — passes guards, mutates, persists, returns projection ──
    @Test
    void happyPath_passesGuards_mutatesPersistsAndReturnsResponse() {
        Room room = existingRoom();
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomExistencePort.existsByBuildingAndFloorAndCode(any(), anyInt(), any())).thenReturn(false);
        when(roomStateGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomRenamedResult response = handler().handle(new RenameRoomCommand(room.id(), "LAB"));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomStateGateway).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.name()).isEqualTo(RoomName.of(RoomLocation.of("F", 2), "LAB"));
        assertThat(saved.recordedEvents()).anyMatch(e -> e instanceof RoomRenamedEvent);
        assertThat(response.id()).isEqualTo(room.id());
        assertThat(response.oldCode()).isEqualTo("01");
        assertThat(response.newCode()).isEqualTo("LAB");
        assertThat(response.name()).isEqualTo("F.02LAB");
    }

    @Test
    void guardsRunInOrder_loadThenGateThenSave() {
        Room room = existingRoom();
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomExistencePort.existsByBuildingAndFloorAndCode(any(), anyInt(), any())).thenReturn(false);
        when(roomStateGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RenameRoomCommand(room.id(), "LAB"));

        var inOrder = org.mockito.Mockito.inOrder(roomStateGateway, roomExistencePort);
        inOrder.verify(roomStateGateway).loadById(any());
        inOrder.verify(roomExistencePort).existsByBuildingAndFloorAndCode(any(), anyInt(), any());
        inOrder.verify(roomStateGateway).save(any());
    }
}
