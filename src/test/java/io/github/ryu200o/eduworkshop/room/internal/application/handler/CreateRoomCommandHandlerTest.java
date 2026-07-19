package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRoomCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUniquenessPolicy uniquenessPolicy;

    private CreateRoomCommandHandler handler() {
        return new CreateRoomCommandHandler(roomRepository, uniquenessPolicy);
    }

    // ── Step 1: RAM guard (Local invariant) blocks malformed input BEFORE any IO ──
    @Test
    void ramGuard_rejectsBlankName_withoutTouchingPorts() {
        CreateRoomCommand badName = new CreateRoomCommand("F", 2, 1, "", 50);

        assertThatThrownBy(() -> handler().handle(badName))
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    @Test
    void ramGuard_rejectsNonPositiveCode_withoutTouchingPorts() {
        CreateRoomCommand badCode = new CreateRoomCommand("F", 2, 0, "F-201", 50);

        assertThatThrownBy(() -> handler().handle(badCode))
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    @Test
    void ramGuard_rejectsNonPositiveFloor_withoutTouchingPorts() {
        CreateRoomCommand badFloor = new CreateRoomCommand("F", 0, 1, "F-201", 50);

        assertThatThrownBy(() -> handler().handle(badFloor))
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    // ── Step 2: Domain guard (Global invariant via policy) blocks duplicate code, never persists ──
    @Test
    void domainGuard_rejectsDuplicateCode_andDoesNotSave() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(DuplicateRoomException.class);

        verify(uniquenessPolicy).isCodeUnique(any(), anyInt());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 2b: Domain guard (name) blocks duplicate name, never persists ──
    @Test
    void domainGuard_rejectsDuplicateName_andDoesNotSave() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(DuplicateRoomException.class);

        verify(uniquenessPolicy).isCodeUnique(any(), anyInt());
        verify(uniquenessPolicy).isNameUnique(any(), any());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 3: Happy path — passes both guards, persists, returns id ──
    @Test
    void happyPath_passesGuards_persists_andReturnsId() {
        CreateRoomCommand command = new CreateRoomCommand("f", 2, 1, "F-201", 50); // lowercase building
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateRoomCommand.Result result = handler().handle(command);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room persisted = captor.getValue();

        assertThat(result.id()).isEqualTo(persisted.id().value());
        assertThat(result.name()).isEqualTo(persisted.name().asString());
        assertThat(persisted.name()).isEqualTo(RoomName.of("F-201"));
        assertThat(persisted.location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(persisted.code()).isEqualTo(1);
        assertThat(persisted.capacity()).isEqualTo(50);
    }

    @Test
    void guardsRunInOrder_policyCheckedBeforeSave() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command);

        var inOrder = org.mockito.Mockito.inOrder(uniquenessPolicy, roomRepository);
        inOrder.verify(uniquenessPolicy).isCodeUnique(any(), anyInt());
        inOrder.verify(uniquenessPolicy).isNameUnique(any(), any());
        inOrder.verify(roomRepository).save(any());
    }
}
