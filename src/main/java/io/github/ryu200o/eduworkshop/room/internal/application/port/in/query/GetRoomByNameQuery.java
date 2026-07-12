package io.github.ryu200o.eduworkshop.room.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.shared.cqs.Query;

/**
 * Query to look up a single room by its canonical name (e.g. "F.201"). The raw string is parsed and
 * validated into a {@code RoomName} value object by the handler (RAM self-defense) before lookup.
 */
public record GetRoomByNameQuery(String roomName) implements Query<RoomResponse> {
}
