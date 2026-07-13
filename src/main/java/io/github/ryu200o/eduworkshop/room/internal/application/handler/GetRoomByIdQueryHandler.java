package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByIdQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.cqs.QueryHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for {@link GetRoomByIdQuery}. CQRS bypass: reads a projection straight from the query
 * port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetRoomByIdQueryHandler implements QueryHandler<GetRoomByIdQuery, RoomDetailView> {

    private final RoomQueryPort roomQueryPort;

    GetRoomByIdQueryHandler(RoomQueryPort roomQueryPort) {
        this.roomQueryPort = roomQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetailView handle(@NonNull GetRoomByIdQuery query) {
        return roomQueryPort.findById(query.roomId())
                .orElseThrow(() -> new RoomNotFoundException("id=" + query.roomId()));
    }
}
