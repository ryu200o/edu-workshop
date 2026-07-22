package io.github.ryu200o.eduworkshop.room.internal.facade;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Package-private implementation of {@link RoomExposeAPI} — the Module Facade for Room.
 * Resides inside the information-hiding boundary (internal/facade/). Coordinates directly
 * with application ports ({@link RoomReader}) — no Command/Query Bus involved, because this
 * is a trusted cross-module collaboration, not an external entry point (per ADR 0010).
 *
 * <p>Delegates to {@link RoomReader} (jOOQ read adapter) for the actual query.
 */
@Component
class RoomExposeAPIImpl implements RoomExposeAPI {

    private final RoomReader roomReader;

    RoomExposeAPIImpl(RoomReader roomReader) {
        this.roomReader = roomReader;
    }

    @Override
    public Optional<RoomSnapshot> findRoomSnapshot(UUID roomId) {
        return roomReader.findById(RoomId.of(roomId))
                .map(view -> new RoomSnapshot(
                        view.id(),
                        view.name(),
                        new RoomSnapshot.Location(view.building(), view.floor())
                ));
    }
}
