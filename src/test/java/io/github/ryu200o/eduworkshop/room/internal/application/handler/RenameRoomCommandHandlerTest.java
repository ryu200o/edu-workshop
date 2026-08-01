package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameRoomCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private RenameRoomCommandHandler handler() {
        return new RenameRoomCommandHandler(roomRepository, clock, roomDomainEventPublisher);
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

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id.value(), "F-202")))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void ramGuard_rejectsBlankName_withoutSaving() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(id.value(), "")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void sameName_isIdempotent_noSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        RenameRoomCommand.Result response = handler().handle(new RenameRoomCommand(room.id().value(), "F-201"));

        assertThat(response.oldName()).isEqualTo("F-201");
        assertThat(response.newName()).isEqualTo("F-201");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void duplicateName_doesNotPersist() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(roomRepository.existsByName(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new RenameRoomCommand(room.id().value(), "LAB-101")))
                .isInstanceOf(DuplicateRoomNameException.class);

        verify(roomRepository, never()).save(any());
    }

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
        ArgumentCaptor<List> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(roomDomainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).anyMatch(e -> e instanceof RoomRenamedEvent);
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.oldName()).isEqualTo("F-201");
        assertThat(response.newName()).isEqualTo("LAB-101");
    }

    @Test
    void happyPath_loadsThenChecksExistsThenSaves() {
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
