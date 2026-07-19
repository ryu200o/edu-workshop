package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRoomCapacityCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    private ChangeRoomCapacityCommandHandler handler() {
        return new ChangeRoomCapacityCommandHandler(roomRepository);
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

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(id.value(), 80)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository, never()).save(any());
    }

    // ── capacity invariant: the DOMAIN (Room.changeCapacity) rejects non-positive capacity ──
    // (Double-guard removed in refactor: handler no longer re-checks capacity>0, domain is the single source.)
    @Test
    void domainRejectsNonPositiveCapacity_withoutSaving() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 0)))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(room.id().value(), -3)))
                .isInstanceOf(RoomDomainException.class);

        verify(roomRepository, never()).save(any());
    }

    // ── Idempotency: same capacity ⇒ no save, returns the entity's CURRENT updatedAt ──
    @Test
    void sameCapacity_isIdempotent_noSave_returnsExistingUpdatedAt() {
        Instant fixedUpdated = Instant.parse("2026-03-15T00:00:00Z");
        Room room = Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), 1, 50, RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), fixedUpdated);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        ChangeRoomCapacityCommand.Result response = handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 50));

        assertThat(response.oldCapacity()).isEqualTo(50);
        assertThat(response.newCapacity()).isEqualTo(50);
        assertThat(response.updatedAt()).isEqualTo(fixedUpdated);   // NOT Instant.now()
        verify(roomRepository, never()).save(any());
    }

    // ── Step 3: Happy path — passes guards, mutates, persists, returns projection ──
    @Test
    void happyPath_passesGuards_mutatesPersistsAndReturnsResponse() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeRoomCapacityCommand.Result response = handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 80));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.capacity()).isEqualTo(80);
        assertThat(saved.recordedEvents()).anyMatch(e -> e instanceof RoomCapacityChanged);
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.oldCapacity()).isEqualTo(50);
        assertThat(response.newCapacity()).isEqualTo(80);
    }

    @Test
    void guardsRunInOrder_loadThenSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 80));

        var ordered = inOrder(roomRepository);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(roomRepository).save(any());
    }
}
