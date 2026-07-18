package io.github.ryu200o.eduworkshop.room.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByIdQuery;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.GetRoomByNameQuery;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driving HTTP adapter for the Room READ side (Query). Accepts only data-reading HTTP methods (GET) and
 * talks exclusively to the shared {@link QueryBus}. Package-private and confined to the
 * module's internal boundary. Error handling is centralized in {@link RoomExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/rooms")
class RoomQueryController {

    private final QueryBus queryBus;

    RoomQueryController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/{id}")
    RoomDetailView getById(@PathVariable UUID id) {
        var query = new GetRoomByIdQuery(id);
        return queryBus.execute(query);
    }

    @GetMapping("/by-name/{name}")
    RoomSummaryView getByName(@PathVariable String name) {
        var query = new GetRoomByNameQuery(name);
        return queryBus.execute(query);
    }
}
