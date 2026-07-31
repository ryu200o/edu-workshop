package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
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
    private RoomRepository roomRepository;

    @Mock
    private RoomDomainEventPublisher roomDomainEventPublisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private CreateRoomCommandHandler handler() {
        return new CreateRoomCommandHandler(roomRepository, clock, roomDomainEventPublisher);
    }

    @Test
    void ramGuard_rejectsBlankName_withoutTouchingPorts() {
        CreateRoomCommand badName = new CreateRoomCommand("F", 2, 1, "", 50);

        assertThatThrownBy(() -> handler().handle(badName))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository);
    }

    @Test
    void ramGuard_rejectsNonPositiveCode_withoutTouchingPorts() {
        CreateRoomCommand badCode = new CreateRoomCommand("F", 2, 0, "F-201", 50);

        assertThatThrownBy(() -> handler().handle(badCode))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository);
    }

    @Test
    void ramGuard_rejectsNonPositiveFloor_withoutTouchingPorts() {
        CreateRoomCommand badFloor = new CreateRoomCommand("F", 0, 1, "F-201", 50);

        assertThatThrownBy(() -> handler().handle(badFloor))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository);
    }

    @Test
    void duplicateCode_throwsAndDoesNotPersist() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void duplicateName_throwsAndDoesNotPersist() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void happyPath_checksExists_beforeCreatingAndPersisting() {
        CreateRoomCommand command = new CreateRoomCommand("f", 2, 1, "F-201", 50);
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateRoomCommand.Result result = handler().handle(command);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room persisted = captor.getValue();

        assertThat(result.id()).isEqualTo(persisted.id().value());
        assertThat(persisted.name()).isEqualTo(RoomName.of("F-201"));
        assertThat(persisted.location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(persisted.code()).isEqualTo(RoomCode.of(1));
        assertThat(persisted.capacity()).isEqualTo(RoomCapacity.of(50));
    }

    @Test
    void happyPath_checksExistsInOrderBeforeSaving() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(roomRepository.existsByCoordinate(any(), any(RoomCode.class))).thenReturn(false);
        when(roomRepository.existsByName(any(), any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command);

        var inOrder = org.mockito.Mockito.inOrder(roomRepository);
        inOrder.verify(roomRepository).existsByCoordinate(any(), any(RoomCode.class));
        inOrder.verify(roomRepository).existsByName(any(), any());
        inOrder.verify(roomRepository).save(any());
    }
}
