package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for relocating a room (changing its building/floor; code unchanged). Enforces the
 * multi-tier duplicate guard in performance order: load (write side) → RAM (VO self-validation) →
 * idempotency → DB (target-coordinate uniqueness) → domain mutation → persist. Package-private:
 * reachable only via the module {@code CommandBus}.
 */
@Component
class RelocateRoomCommandHandler implements CommandHandler<RelocateRoomCommand, RelocateRoomCommand.Result> {

    private final RoomRepository roomRepository;

    RelocateRoomCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public RelocateRoomCommand.Result handle(@NonNull RelocateRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = RoomCommandGuard.loadForMutation(roomRepository, command.roomId());

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new location.
        RoomLocation newLocation = RoomLocation.of(command.newBuilding(), command.newFloor());

        // Step 3 — Idempotency: same location ⇒ no change, no gate, no persist.
        //         Returns the current entity's updatedAt (NOT Instant.now()) — nothing changed.
        if (newLocation.equals(room.location())) {
            return toResult(room, room.location());
        }

        // Step 4 — DB guard (global invariant): target coordinate (new location + same code) must be free
        //         of *other* rooms.
        RoomCommandGuard.assertCoordinateFree(roomRepository, room.name(), newLocation);

        // Step 5 — Domain mutation (recomputes name, records RoomRenamedEvent) then persist.
        RoomLocation oldLocation = room.location();
        room.relocateTo(newLocation);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldLocation);
    }

    private static RelocateRoomCommand.@NonNull Result toResult(@NonNull Room room, RoomLocation oldLocation) {
        return new RelocateRoomCommand.Result(
                room.id(), oldLocation, room.location(), room.name().asString(), room.updatedAt());
    }
}
