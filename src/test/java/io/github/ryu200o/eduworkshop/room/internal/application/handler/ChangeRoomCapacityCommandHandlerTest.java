package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomDomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private ChangeRoomCapacityCommandHandler handler() {
        return new ChangeRoomCapacityCommandHandler(roomRepository, clock, roomDomainEventPublisher);
    }

    private static Room existingRoom() {
        return Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), RoomCode.of(1), RoomCapacity.of(50), RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(id.value(), 80)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void domainRejectsNonPositiveCapacity_withoutSaving() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler().handle(new ChangeRoomCapacityCommand(room.id().value(), -3)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void sameCapacity_isIdempotent_noSave_returnsExistingUpdatedAt() {
        Instant fixedUpdated = Instant.parse("2026-03-15T00:00:00Z");
        Room room = Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), RoomCode.of(1), RoomCapacity.of(50), RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), fixedUpdated);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        ChangeRoomCapacityCommand.Result response = handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 50));

        assertThat(response.oldCapacity()).isEqualTo(50);
        assertThat(response.newCapacity()).isEqualTo(50);
        assertThat(response.updatedAt()).isEqualTo(fixedUpdated);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void happyPath_passesGuards_mutatesPersistsAndReturnsResponse() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeRoomCapacityCommand.Result response = handler().handle(new ChangeRoomCapacityCommand(room.id().value(), 80));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.capacity()).isEqualTo(RoomCapacity.of(80));
        ArgumentCaptor<List> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(roomDomainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).anyMatch(e -> e instanceof RoomCapacityChanged);
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
