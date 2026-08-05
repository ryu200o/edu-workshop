package io.github.ryu200o.eduworkshop.room.internal.facade;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the Room module facade ({@link RoomExposeAPI} impl). Proves the facade delegates
 * the {@code existsById} presence check straight to the read port ({@link RoomReader#existsById}),
 * bypassing the full-projection {@code getById} materialization.
 */
@ExtendWith(MockitoExtension.class)
class RoomExposeAPIImplTest {

    @Mock
    private RoomReader roomReader;

    private RoomExposeAPI exposeApi() {
        return new RoomExposeAPIImpl(roomReader);
    }

    @Test
    void existsById_delegatesToReaderExistencePredicate() {
        UUID roomId = UUID.randomUUID();
        when(roomReader.existsById(RoomId.of(roomId))).thenReturn(true);

        assertThat(exposeApi().existsById(roomId)).isTrue();

        verify(roomReader).existsById(RoomId.of(roomId));
    }

    @Test
    void existsById_absent_delegatesAndReturnsFalse() {
        UUID roomId = UUID.randomUUID();
        when(roomReader.existsById(RoomId.of(roomId))).thenReturn(false);

        assertThat(exposeApi().existsById(roomId)).isFalse();

        verify(roomReader).existsById(RoomId.of(roomId));
    }

    @Test
    void existsById_doesNotMaterializeFullProjection() {
        UUID roomId = UUID.randomUUID();
        when(roomReader.existsById(RoomId.of(roomId))).thenReturn(true);

        exposeApi().existsById(roomId);

        verify(roomReader).existsById(RoomId.of(roomId));
        verify(roomReader, org.mockito.Mockito.never()).getById(RoomId.of(roomId));
    }
}