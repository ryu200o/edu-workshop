package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRoomCommandHandlerTest {

    @Mock
    private RoomExistencePort roomExistencePort;

    @Mock
    private RoomStateGateway roomStateGateway;

    private CreateRoomCommandHandler handler() {
        return new CreateRoomCommandHandler(roomExistencePort, roomStateGateway);
    }

    // ── Step 1: RAM guard (Local invariant) blocks malformed input BEFORE any DB call ──
    @Test
    void ramGuard_rejectsMalformedInput_withoutTouchingPorts() {
        CreateRoomCommand badCode = new CreateRoomCommand("F", 2, 50, ""); // blank code is invalid

        assertThatThrownBy(() -> handler().handle(badCode))
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomExistencePort, roomStateGateway);
    }

    @Test
    void ramGuard_rejectsNonPositiveFloor_withoutTouchingPorts() {
        CreateRoomCommand badFloor = new CreateRoomCommand("F", 0, 50, "01");

        assertThatThrownBy(() -> handler().handle(badFloor))
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomExistencePort, roomStateGateway);
    }

    // ── Step 2: DB guard (Global invariant) blocks duplicates, never persists ──
    @Test
    void dbGuard_rejectsDuplicate_andDoesNotSave() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 50, "01");
        when(roomExistencePort.existsByNameAndLocation(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(DuplicateRoomException.class);

        verify(roomExistencePort).existsByNameAndLocation(any(), any());
        verify(roomStateGateway, never()).save(any());
    }

    // ── Step 3: Happy path — passes both guards, persists, returns id ──
    @Test
    void happyPath_passesGuards_persists_andReturnsId() {
        CreateRoomCommand command = new CreateRoomCommand("f", 2, 50, "01"); // lowercase building
        when(roomExistencePort.existsByNameAndLocation(any(), any())).thenReturn(false);
        when(roomStateGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = handler().handle(command);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomStateGateway).save(captor.capture());
        Room persisted = captor.getValue();

        assertThat(id).isEqualTo(persisted.id());
        assertThat(persisted.name()).isEqualTo(RoomName.of(RoomLocation.of("F", 2), "01"));
        assertThat(persisted.location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(persisted.capacity()).isEqualTo(50);
    }

    @Test
    void guardsRunInOrder_existenceCheckedBeforeSave() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 50, "01");
        when(roomExistencePort.existsByNameAndLocation(any(), any())).thenReturn(false);
        when(roomStateGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command);

        var inOrder = org.mockito.Mockito.inOrder(roomExistencePort, roomStateGateway);
        inOrder.verify(roomExistencePort).existsByNameAndLocation(any(), any());
        inOrder.verify(roomStateGateway).save(any());
    }
}
