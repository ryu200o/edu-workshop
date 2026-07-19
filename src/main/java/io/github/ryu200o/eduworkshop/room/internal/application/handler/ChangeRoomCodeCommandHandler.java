package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for changing a room's independent {@code code} (an int used only for FE ordering).
 * This is a silent mutation — the domain emits NO event. The code uniqueness guard (building + floor +
 * code) still applies, enforced by the DB constraint and the race-proof gate in the write adapter.
 * Package-private: reachable only via the module {@code CommandBus}.
 */
@Component
class ChangeRoomCodeCommandHandler implements CommandHandler<ChangeRoomCodeCommand, ChangeRoomCodeCommand.Result> {

    private final RoomRepository roomRepository;

    ChangeRoomCodeCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public ChangeRoomCodeCommand.Result handle(ChangeRoomCodeCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // Step 2 — Idempotency: same code ⇒ no change, no gate, no persist.
        if (command.newCode() == room.code()) {
            return toResult(room, room.code());
        }

        // Step 3 — DB guard (global invariant): target coordinate must be free of *other* rooms.
        if (command.newCode() <= 0) {
            throw new RoomDomainException("Room code must be greater than zero.");
        }
        if (roomRepository.existsByCoordinate(room.location(), command.newCode())) {
            throw new DuplicateRoomException(room.name(), room.location());
        }

        // Step 4 — Domain mutation (silent, no event) then persist.
        int oldCode = room.code();
        room.changeCode(command.newCode());
        Room saved = roomRepository.save(room);
        return toResult(saved, oldCode);
    }

    private static ChangeRoomCodeCommand.Result toResult(Room room, int oldCode) {
        return new ChangeRoomCodeCommand.Result(
                room.id().value(), oldCode, room.code(), room.updatedAt());
    }
}
