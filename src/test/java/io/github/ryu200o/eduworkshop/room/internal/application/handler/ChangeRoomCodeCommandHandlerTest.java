package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
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
class ChangeRoomCodeCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private ChangeRoomCodeCommandHandler handler() {
        return new ChangeRoomCodeCommandHandler(roomRepository, clock, roomDomainEventPublisher);
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

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(id.value(), 2)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void ramGuard_rejectsNonPositiveCode_withoutTouchingPorts() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(id.value(), 0)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void duplicateCode_doesNotPersist() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(room.id().value(), 2)))
                .isInstanceOf(DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void sameCode_isIdempotent_noGateNoSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        handler().handle(new ChangeRoomCodeCommand(room.id().value(), 1));

        assertThat(room.code()).isEqualTo(RoomCode.of(1));
        verify(roomRepository, never()).save(any());
    }

    @Test
    void happyPath_changesCodeSilently_persists() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new ChangeRoomCodeCommand(room.id().value(), 99));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.code()).isEqualTo(RoomCode.of(99));
        assertThat(saved.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .isEmpty();
        assertThat(saved.id()).isEqualTo(room.id());
    }

    @Test
    void happyPath_loadsThenChecksExistsThenSaves() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new ChangeRoomCodeCommand(room.id().value(), 99));

        var ordered = org.mockito.Mockito.inOrder(roomRepository);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(roomRepository).existsByCoordinate(any(), any(RoomCode.class));
        ordered.verify(roomRepository).save(any());
    }
}
