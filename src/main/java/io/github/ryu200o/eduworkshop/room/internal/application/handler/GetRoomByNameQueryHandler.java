package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByNameQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for {@link GetRoomByNameQuery}. Parses/validates the raw name into a {@code RoomName}
 * value object (RAM self-defense) before delegating to the read port. CQRS bypass; package-private;
 * side-effect free.
 */
@Component
class GetRoomByNameQueryHandler implements QueryHandler<GetRoomByNameQuery, RoomSummaryView> {

    private final RoomReader roomReader;

    GetRoomByNameQueryHandler(RoomReader roomReader) {
        this.roomReader = roomReader;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomSummaryView handle(GetRoomByNameQuery query) {
        RoomName name = RoomName.ofRaw(query.roomName()); // RAM self-defense; opaque, no reverse-parse
        return roomReader.findByName(name)
                .orElseThrow(() -> new RoomNotFoundException("name=" + name.asString()));
    }
}
