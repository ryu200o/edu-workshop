package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByNameQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRoomByNameQueryHandlerTest {

    @Mock
    private RoomReader roomReader;

    private GetRoomByNameQueryHandler handler() {
        return new GetRoomByNameQueryHandler(roomReader);
    }

    @Test
    void happyPath_returnsProjection() {
        RoomSummaryView expected = new RoomSummaryView(UUID.randomUUID(), "F-201", "F", 2);
        when(roomReader.findByName(RoomName.of("F-201"))).thenReturn(Optional.of(expected));

        RoomSummaryView result = handler().handle(new GetRoomByNameQuery("F-201"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void blankName_rejectedInRam_beforeTouchingPort() {
        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("   ")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomReader);
    }

    @Test
    void notFound_throwsRoomNotFoundException() {
        when(roomReader.findByName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("F-201")))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomReader).findByName(RoomName.of("F-201"));
    }

    @Test
    void blankName_neverCallsPort() {
        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomReader, never()).findByName(any());
    }
}
