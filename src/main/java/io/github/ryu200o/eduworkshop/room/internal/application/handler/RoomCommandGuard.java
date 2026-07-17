package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;

import java.util.UUID;

/**
 * Application-layer guard helpers shared by Room command handlers. This is orchestration (load + outbound
 * port coordination), NOT domain logic — keep it out of the Domain. Its job is to remove the copy-pasted
 * load → existence-check pipeline that previously lived in every identity-changing handler.
 */
final class RoomCommandGuard {

    private RoomCommandGuard() {
    }

    /**
     * Loads the aggregate for a write-side mutation, translating an absent room into
     * {@link RoomNotFoundException}.
     */
    static Room loadForMutation(RoomRepository repository, UUID roomId) {
        return repository.loadById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId.toString()));
    }

    /**
     * Global-uniqueness gate: throws {@link DuplicateRoomException} if any OTHER room already occupies the
     * given coordinate. Idempotency (skip when the coordinate is the room's own) is the caller's concern and
     * must be checked before invoking this.
     */
    static void assertCoordinateFree(RoomRepository repository, RoomName name, RoomLocation location) {
        if (repository.existsByCoordinate(location.building(), location.floor(), name.code())) {
            throw new DuplicateRoomException(name, location);
        }
    }
}
