package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelocateRoomCommandHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUniquenessPolicy uniquenessPolicy;

    private RelocateRoomCommandHandler handler() {
        return new RelocateRoomCommandHandler(roomRepository, uniquenessPolicy);
    }

    // Fixtures bypass the uniqueness gate (already-unique room): a policy that always reports "unique".
    private static final RoomUniquenessPolicy ALWAYS_UNIQUE = new RoomUniquenessPolicy() {
        @Override
        public boolean isCodeUnique(RoomLocation location, int code) {
            return true;
        }

        @Override
        public boolean isNameUnique(RoomLocation location, RoomName name) {
            return true;
        }
    };

    private static Room existingRoom() {
        RoomLocation location = RoomLocation.of("F", 2);
        return Room.create(RoomName.of("F-201"), location, 1, 50, ALWAYS_UNIQUE);
    }

    // ── Step 1: load failure ──
    @Test
    void roomNotFound_whenLoadReturnsEmpty_throws() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id.value(), "G", 3)))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).loadById(any());
        verify(roomRepository, never()).save(any());
    }

    // ── Step 2: RAM guard (local invariant) blocks malformed location (load happens first, no save/gate) ──
    @Test
    void ramGuard_rejectsInvalidLocation_withoutTouchingPorts() {
        RoomId id = RoomId.of(UUID.randomUUID());
        when(roomRepository.loadById(id)).thenReturn(Optional.of(existingRoom()));

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(id.value(), "G", 0)))
                .isInstanceOf(RoomDomainException.class);

        verify(roomRepository).loadById(any());
        verify(uniquenessPolicy, never()).isCodeUnique(any(), anyInt());
        verify(roomRepository, never()).save(any());
    }

    // The duplicate-code rejection is owned by the aggregate (RoomTest). When the policy reports a collision
    // the aggregate throws before save, so nothing is persisted.
    @Test
    void duplicateCode_doesNotPersist_becauseAggregateRejectsFirst() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3)))
                .isInstanceOf(DuplicateRoomCodeException.class);

        verify(roomRepository, never()).save(any());
    }

    // ── Idempotency: same location ⇒ no gate, no save, returns the entity's CURRENT updatedAt ──
    @Test
    void sameLocation_isIdempotent_noGateNoSave_returnsExistingUpdatedAt() {
        Instant fixedUpdated = Instant.parse("2026-03-15T00:00:00Z");
        Room room = Room.reconstruct(
                RoomId.of(UUID.randomUUID()), RoomName.of("F-201"),
                RoomLocation.of("F", 2), 1, 50, RoomState.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), fixedUpdated);
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));

        RelocateRoomCommand.Result response = handler().handle(new RelocateRoomCommand(room.id().value(), "F", 2));

        assertThat(response.oldLocation()).isEqualTo(room.location());
        assertThat(response.newLocation()).isEqualTo(room.location());
        assertThat(response.updatedAt()).isEqualTo(fixedUpdated);   // NOT Instant.now()
        verify(uniquenessPolicy, never()).isCodeUnique(any(), anyInt());
        verify(roomRepository, never()).save(any());
    }

    // ── Happy path — load → delegate (aggregate enforces uniqueness internally) → persist → return ──
    @Test
    void happyPath_passesGuards_mutatesPersistsAndReturnsResponse() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RelocateRoomCommand.Result response = handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.location()).isEqualTo(RoomLocation.of("G", 3));
        assertThat(saved.name()).isEqualTo(RoomName.of("F-201")); // name is preserved (decoupled)
        assertThat(saved.code()).isEqualTo(1);                    // code is preserved
        assertThat(saved.recordedEvents()).anyMatch(e -> e instanceof RoomRelocatedEvent);
        assertThat(response.id()).isEqualTo(room.id().value());
        assertThat(response.oldLocation()).isEqualTo(RoomLocation.of("F", 2));
        assertThat(response.newLocation()).isEqualTo(RoomLocation.of("G", 3));
    }

    @Test
    void happyPath_loadsThenDelegatesThenSaves() {
        Room room = existingRoom();
        when(roomRepository.loadById(room.id())).thenReturn(Optional.of(room));
        when(uniquenessPolicy.isCodeUnique(any(), anyInt())).thenReturn(true);
        when(uniquenessPolicy.isNameUnique(any(), any())).thenReturn(true);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new RelocateRoomCommand(room.id().value(), "G", 3));

        var ordered = inOrder(roomRepository, uniquenessPolicy);
        ordered.verify(roomRepository).loadById(any());
        ordered.verify(uniquenessPolicy).isCodeUnique(any(), anyInt());
        ordered.verify(uniquenessPolicy).isNameUnique(any(), any());
        ordered.verify(roomRepository).save(any());
    }
}
