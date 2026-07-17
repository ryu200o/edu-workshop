package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for changing a room's capacity. Enforces the guard pipeline in performance order:
 * load (write side) → RAM (self-defense on the new capacity) → idempotency → domain mutation → persist.
 * Package-private: reachable only via the module {@code CommandBus}.
 */
@Component
class ChangeRoomCapacityCommandHandler implements CommandHandler<ChangeRoomCapacityCommand, ChangeRoomCapacityCommand.Result> {

    private final RoomRepository roomRepository;

    ChangeRoomCapacityCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public ChangeRoomCapacityCommand.Result handle(@NonNull ChangeRoomCapacityCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = RoomCommandGuard.loadForMutation(roomRepository, command.roomId());

        // Step 2 — Idempotency: same capacity ⇒ no change, no save.
        //         Returns the current entity's updatedAt (NOT Instant.now()) — nothing changed.
        //         (The capacity>0 invariant is enforced solely by the domain in Room.changeCapacity.)
        if (command.newCapacity() == room.capacity()) {
            return toResult(room);
        }

        // Step 3 — Domain mutation (records RoomCapacityChanged) then persist.
        int oldCapacity = room.capacity();
        room.changeCapacity(command.newCapacity());
        Room saved = roomRepository.save(room);
        return new ChangeRoomCapacityCommand.Result(saved.id(), oldCapacity, saved.capacity(), saved.updatedAt());
    }

    private static ChangeRoomCapacityCommand.@NonNull Result toResult(@NonNull Room room) {
        return new ChangeRoomCapacityCommand.Result(
                room.id(), room.capacity(), room.capacity(), room.updatedAt());
    }
}
