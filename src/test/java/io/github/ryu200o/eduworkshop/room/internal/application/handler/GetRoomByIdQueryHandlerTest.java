package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByIdQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRoomByIdQueryHandlerTest {

    @Mock
    private RoomQueryPort roomQueryPort;

    private GetRoomByIdQueryHandler handler() {
        return new GetRoomByIdQueryHandler(roomQueryPort);
    }

    @Test
    void happyPath_returnsProjectionFromPort() {
        UUID id = UUID.randomUUID();
        RoomResponse expected = new RoomResponse(id, "F.0201", "F", 2, 50, "ACTIVE");
        when(roomQueryPort.findById(id)).thenReturn(Optional.of(expected));

        RoomResponse result = handler().handle(new GetRoomByIdQuery(id));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void notFound_throwsRoomNotFoundException() {
        UUID id = UUID.randomUUID();
        when(roomQueryPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new GetRoomByIdQuery(id)))
                .isInstanceOf(RoomNotFoundException.class);
    }
}
