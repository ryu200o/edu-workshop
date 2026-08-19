package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
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
class RelocateRoomCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private RelocateRoomCommandHandler handler() {
        return new RelocateRoomCommandHandler(roomRepository, clock, roomDomainEventPublisher);
    }

    private static Room existingRoom() {
        return Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), RoomCode.of(1), RoomCapacity.of(50), RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-15T00:00:00Z"));
    }

    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id.value(), "G", 3)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void ramGuard_rejectsInvalidLocation_withoutTouchingPorts() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id.value(), "G", 0)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void duplicateCode_doesNotPersist() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3)))
                .isInstanceOf(DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void duplicateName_doesNotPersist() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3)))
                .isInstanceOf(DuplicateRoomNameException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void sameLocation_isIdempotent_noGateNoSave() {
        Room room = Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), RoomCode.of(1), RoomCapacity.of(50), RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-15T00:00:00Z"));
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        handler().handle(new RelocateRoomCommand(room.id().value(), "F", 2));

        assertThat(room.location()).isEqualTo(RoomLocation.of("F", 2));
        verify(roomRepository, never()).save(any());
    }

    @Test
    void happyPath_passesGuards_mutatesAndPersists() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.location()).isEqualTo(RoomLocation.of("G", 3));
        assertThat(saved.name()).isEqualTo(RoomName.of("F-201"));
        assertThat(saved.code()).isEqualTo(RoomCode.of(1));
        ArgumentCaptor<List> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(roomDomainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).anyMatch(e -> e instanceof RoomRelocatedEvent);
        assertThat(saved.id()).isEqualTo(room.id());
    }

    @Test
    void happyPath_loadsThenChecksExistsThenSaves() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3));

        var ordered = inOrder(roomRepository);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(roomRepository).existsByCoordinate(any(), any(RoomCode.class));
        ordered.verify(roomRepository).existsByName(any(), any());
        ordered.verify(roomRepository).save(any());
    }
}
