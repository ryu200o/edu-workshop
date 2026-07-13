package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByNameQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
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
    private RoomQueryPort roomQueryPort;

    private GetRoomByNameQueryHandler handler() {
        return new GetRoomByNameQueryHandler(roomQueryPort);
    }

    @Test
    void happyPath_parsesNameThenReturnsProjection() {
        RoomSummaryView expected = new RoomSummaryView(UUID.randomUUID(), "F.0201", "F", 2);
        when(roomQueryPort.findByName(RoomName.ofRaw("F.0201"))).thenReturn(Optional.of(expected));

        RoomSummaryView result = handler().handle(new GetRoomByNameQuery("F.0201"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void malformedName_rejectedInRam_beforeTouchingPort() {
        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("F201"))) // missing dot
                .isInstanceOf(RoomDomainException.class);

        verifyNoInteractions(roomQueryPort);
    }

    @Test
    void notFound_throwsRoomNotFoundException() {
        when(roomQueryPort.findByName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("F.0201")))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomQueryPort).findByName(RoomName.ofRaw("F.0201"));
    }

    @Test
    void malformedName_neverCallsPort() {
        assertThatThrownBy(() -> handler().handle(new GetRoomByNameQuery("bad")))
                .isInstanceOf(RoomDomainException.class);

        verify(roomQueryPort, never()).findByName(any());
    }
}
