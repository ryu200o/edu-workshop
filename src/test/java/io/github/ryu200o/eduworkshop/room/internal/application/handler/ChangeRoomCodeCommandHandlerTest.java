package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRoomCodeCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUniquenessPolicy uniquenessPolicy;

    private ChangeRoomCodeCommandHandler handler() {
        return new ChangeRoomCodeCommandHandler(roomRepository, uniquenessPolicy);
    }

    // Fixtures bypass the uniqueness gate (already-unique room): a policy that always reports "unique".
    private static final RoomUniquenessPolicy ALWAYS_UNIQUE = new RoomUniquenessPolicy() {
        @Override
        public boolean isCodeUnique(RoomLocation location, RoomCode code) {
            return true;
        }

        @Override
        public boolean isNameUnique(RoomLocation location, RoomName name) {
            return true;
        }
    };

    private static Room existingRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        Instant now = Instant.now();
        return Room.create(RoomId.generate(), RoomName.of("F-201"), location, RoomCode.of(1),
                RoomCapacity.of(50), now, now, ALWAYS_UNIQUE);
    }

    // ── Step 1: load failure ──
    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(id.value(), 2)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 2: RAM guard (local invariant) blocks non-positive code (load happens first, no save/gate) ──
    @Test
    void ramGuard_rejectsNonPositiveCode_withoutTouchingPorts() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        // The code invariant is owned by the RoomCode VO: the handler builds it and the VO self-validates.
        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(id.value(), 0)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository).loadById(any());
        verify(uniquenessPolicy, never()).isCodeUnique(any(), any(RoomCode.class));
        verify(roomRepository, never()).save(any());
    }

    // The duplicate-code rejection is owned by the aggregate (RoomTest). When the policy reports a collision
    // the aggregate throws before save, so nothing is persisted.
    @Test
    void duplicateCode_doesNotPersist_becauseAggregateRejectsFirst() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(new ChangeRoomCodeCommand(room.id().value(), 2)))
                .isInstanceOf(DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }

    // ── Idempotency: same code ⇒ no gate, no save ──
    @Test
    void sameCode_isIdempotent_noGateNoSave() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        ChangeRoomCodeCommand.Result response = handler().handle(new ChangeRoomCodeCommand(room.id().value(), 1));

        assertThat(response.oldCode()).isEqualTo(1);
        assertThat(response.newCode()).isEqualTo(1);
        verify(uniquenessPolicy, never()).isCodeUnique(any(), any(RoomCode.class));
        verify(roomRepository, never()).save(any());
    }

    // ── Happy path — silent change, persists, no RoomRenamedEvent ──
    @Test
    void happyPath_changesCodeSilently_persists_andReturnsResponse() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeRoomCodeCommand.Result response = handler().handle(new ChangeRoomCodeCommand(room.id().value(), 99));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.code()).isEqualTo(RoomCode.of(99));
        assertThat(saved.recordedEvents())
                .filteredOn(RoomRenamedEvent.class::isInstance)
                .isEmpty(); // silent — no rename event
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.oldCode()).isEqualTo(1);
        assertThat(response.newCode()).isEqualTo(99);
    }

    @Test
    void happyPath_loadsThenDelegatesThenSaves() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), any(RoomCode.class))).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new ChangeRoomCodeCommand(room.id().value(), 99));

        var ordered = org.mockito.Mockito.inOrder(roomRepository, uniquenessPolicy);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(uniquenessPolicy).isCodeUnique(any(), any(RoomCode.class));
        ordered.verify(roomRepository).save(any());
    }
}
