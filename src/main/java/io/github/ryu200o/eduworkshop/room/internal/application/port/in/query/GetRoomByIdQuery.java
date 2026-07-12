package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.shared.cqs.Query;

import java.util.UUID;

/**
 * Query to look up a single room by its identifier.
 */
public record GetRoomByIdQuery(UUID roomId) implements Query<RoomResponse> {
}
