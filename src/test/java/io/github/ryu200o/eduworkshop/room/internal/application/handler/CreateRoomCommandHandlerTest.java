package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
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
    private RoomUniquenessPolicy uniquenessPolicy;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
    }

    private CreateRoomCommandHandler handler() {
        return new CreateRoomCommandHandler(roomRepository, uniquenessPolicy, clock);
    }

    // The handler is THIN: it builds VOs, delegates to the aggregate (which owns the invariant via the
    // policy), and persists. Uniqueness is asserted inside RoomTest — the handler only passes the policy
    // through. Here we verify the handler's own job: local VO guards and the load→build→save flow.

    // ── Step 1: RAM guard (Local invariant) blocks malformed input BEFORE any IO ──
    @Test
    void ramGuard_rejectsBlankName_withoutTouchingPorts() {
        CreateRoomCommand badName = new CreateRoomCommand("F", 2, 1, "", 50);

        assertThatThrownBy(() -> handler().handle(badName))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    @Test
    void ramGuard_rejectsNonPositiveCode_withoutTouchingPorts() {
        CreateRoomCommand badCode = new CreateRoomCommand("F", 2, 0, "F-201", 50);

        // The code invariant is owned by the RoomCode VO: the handler builds it and the VO self-validates.
        assertThatThrownBy(() -> handler().handle(badCode))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    @Test
    void ramGuard_rejectsNonPositiveFloor_withoutTouchingPorts() {
        CreateRoomCommand badFloor = new CreateRoomCommand("F", 0, 1, "F-201", 50);

        assertThatThrownBy(() -> handler().handle(badFloor))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomRepository, uniquenessPolicy);
    }

    // ── Happy path: builds VOs, delegates to aggregate (which enforces uniqueness internally),
    //    then persists and returns the id. We stub the policy to "unique" since the invariant is the
    //    aggregate's concern, not the handler's. ──
    @Test
    void happyPath_buildsVosDelegatesAndPersists() {
        CreateRoomCommand command = new CreateRoomCommand("f", 2, 1, "F-201", 50); // lowercase building
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateRoomCommand.Result result = handler().handle(command);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room persisted = captor.getValue();

        // Aggregate normalized the building (uppercase) and free-form name.
        assertThat(result.id()).isEqualTo(persisted.id().value());
        assertThat(persisted.name()).isEqualTo(RoomName.of("F-201"));
        assertThat(persisted.location()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(persisted.code()).isEqualTo(RoomCode.of(1));
        assertThat(persisted.capacity()).isEqualTo(RoomCapacity.of(50));
    }

    @Test
    void happyPath_passesPolicyIntoAggregate_andSavesAfter() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command);

        var inOrder = org.mockito.Mockito.inOrder(uniquenessPolicy, roomRepository);
        // The handler asks the policy (delegated to the aggregate) BEFORE persisting.
        inOrder.verify(uniquenessPolicy).isCodeUnique(any(), any(RoomCode.class));
        inOrder.verify(uniquenessPolicy).isNameUnique(any(), any());
        inOrder.verify(roomRepository).save(any());
    }

    // The duplicate-rejection path is owned by the aggregate (RoomTest). The handler must NOT bypass it:
    // if the policy reports a duplicate, the aggregate throws before save, so nothing is persisted.
    @Test
    void duplicateCode_doesNotPersist_becauseAggregateRejectsFirst() {
        CreateRoomCommand command = new CreateRoomCommand("F", 2, 1, "F-201", 50);
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(command))
                .isInstanceOf(io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }
}
