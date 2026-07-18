package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByIdQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for {@link GetRoomByIdQuery}. CQRS bypass: reads a projection straight from the query
 * port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetRoomByIdQueryHandler implements QueryHandler<GetRoomByIdQuery, RoomDetailView> {

    private final RoomReader roomReader;

    GetRoomByIdQueryHandler(RoomReader roomReader) {
        this.roomReader = roomReader;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetailView handle(GetRoomByIdQuery query) {
        return roomReader.findById(RoomId.of(query.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id=" + query.roomId()));
    }
}
