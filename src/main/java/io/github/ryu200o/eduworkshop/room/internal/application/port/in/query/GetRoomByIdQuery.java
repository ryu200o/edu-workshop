package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Query to look up a single room by its identifier.
 */
public record GetRoomByIdQuery(UUID roomId) implements Query<RoomDetailView> {
}
