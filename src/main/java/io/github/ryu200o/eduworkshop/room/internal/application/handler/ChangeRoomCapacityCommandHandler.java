package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Use-case handler for changing a room's capacity. Enforces the guard pipeline in performance order:
 * load (write side) → RAM (self-defense on the new capacity) → idempotency → domain mutation → persist.
 * Package-private: reachable only via the module {@code CommandBus}.
 */
@Component
class ChangeRoomCapacityCommandHandler implements CommandHandler<ChangeRoomCapacityCommand, ChangeRoomCapacityCommand.Result> {

    private final RoomRepository roomRepository;
    private final Clock clock;

    ChangeRoomCapacityCommandHandler(RoomRepository roomRepository,  Clock clock) {
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChangeRoomCapacityCommand.Result handle(ChangeRoomCapacityCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        // Step 2 — Idempotency: same capacity ⇒ no change, no save.
        //         Returns the current entity's updatedAt (NOT Instant.now()) — nothing changed.
        RoomCapacity newCapacity = RoomCapacity.of(command.newCapacity());
        if (newCapacity.equals(room.capacity())) {
            return toResult(room, room.capacity());
        }

        // Step 3 — Domain mutation (records RoomCapacityChanged) then persist.
        RoomCapacity oldCapacity = room.capacity();
        Instant now = Instant.now(clock);
        room.changeCapacity(newCapacity, now);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldCapacity);
    }

    private static ChangeRoomCapacityCommand.Result toResult(Room room, RoomCapacity oldCapacity) {
        return new ChangeRoomCapacityCommand.Result(
                room.id().value(), oldCapacity.value(), room.capacity().value(), room.updatedAt());
    }
}
