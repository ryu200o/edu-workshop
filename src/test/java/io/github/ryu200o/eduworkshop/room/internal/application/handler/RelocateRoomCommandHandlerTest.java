package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelocateRoomCommandHandlerTest {

    @Mock
    private RoomStateGateway roomStateGateway;

    @Mock
    private RoomExistencePort roomExistencePort;

    private RelocateRoomCommandHandler handler() {
        return new RelocateRoomCommandHandler(roomStateGateway, roomExistencePort);
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

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id, "G", 3)))
                .isInstanceOf(RoomNotFoundException.class);

        verifyNoInteractions(roomExistencePort);
        verify(roomStateGateway, never()).save(any());
    }

    // ── Step 2: RAM guard (local invariant) blocks malformed location BEFORE any DB/port call ──
    @Test
    void ramGuard_rejectsInvalidLocation_withoutTouchingPorts() {
        UUID id = UUID.randomUUID();
        when(roomStateGateway.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id, "G", 0)))
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

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(room.id(), "G", 3)))
                .isInstanceOf(DuplicateRoomException.class);

        verify(roomExistencePort).existsByBuildingAndFloorAndCode(any(), anyInt(), any());
        verify(roomStateGateway, never()).save(any());
    }

    // ── Idempotency: same location ⇒ no gate, no save, returns the entity's CURRENT updatedAt ──
    @Test
    void sameLocation_isIdempotent_noGateNoSave_returnsExistingUpdatedAt() {
        Instant fixedUpdated = Instant.parse("2026-03-15T00:00:00Z");
        Room room = Room.reconstruct(
                UUID.randomUUID(), RoomName.of(RoomLocation.of("F", 2), "01"),
                RoomLocation.of("F", 2), 50, RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), fixedUpdated);
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));

        RelocateRoomCommand.Result response = handler().handle(new RelocateRoomCommand(room.id(), "F", 2));

        assertThat(response.oldLocation()).isEqualTo(room.location());
        assertThat(response.newLocation()).isEqualTo(room.location());
        assertThat(response.updatedAt()).isEqualTo(fixedUpdated);   // NOT Instant.now()
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

        RelocateRoomCommand.Result response = handler().handle(new RelocateRoomCommand(room.id(), "G", 3));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomStateGateway).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.location()).isEqualTo(RoomLocation.of("G", 3));
        assertThat(saved.name()).isEqualTo(RoomName.of(RoomLocation.of("G", 3), "01"));
        assertThat(saved.recordedEvents()).anyMatch(e -> e instanceof RoomRenamedEvent);
        assertThat(response.id()).isEqualTo(room.id());
        assertThat(response.name()).isEqualTo("G.0301");
        assertThat(response.oldLocation()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(response.newLocation()).isEqualTo(RoomLocation.of("G", 3));
    }

    @Test
    void guardsRunInOrder_loadThenGateThenSave() {
        Room room = existingRoom();
        when(roomStateGateway.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomExistencePort.existsByBuildingAndFloorAndCode(any(), anyInt(), any())).thenReturn(false);
        when(roomStateGateway.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RelocateRoomCommand(room.id(), "G", 3));

        var ordered = inOrder(roomStateGateway, roomExistencePort);
        ordered.verify(roomStateGateway).loadById(any());
        ordered.verify(roomExistencePort).existsByBuildingAndFloorAndCode(any(), anyInt(), any());
        ordered.verify(roomStateGateway).save(any());
    }
}
