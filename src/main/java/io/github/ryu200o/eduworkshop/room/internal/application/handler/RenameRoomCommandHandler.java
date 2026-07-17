package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for renaming a room (changing its code; building/floor unchanged). Enforces the
 * multi-tier duplicate guard in performance order: load (write side) → RAM (VO self-validation) →
 * DB (target-coordinate uniqueness) → domain mutation → persist. Package-private: reachable only via
 * the module {@code CommandBus}.
 */
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {

    private final RoomRepository roomRepository;

    RenameRoomCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public RenameRoomCommand.Result handle(@NonNull RenameRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = RoomCommandGuard.loadForMutation(roomRepository, command.roomId());

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new code.
        RoomName candidate = RoomName.of(room.location(), command.newCode());

        // Step 3 — Idempotency: same code ⇒ no coordinate change, no gate, no persist.
        if (candidate.code().equals(room.name().code())) {
            return toResult(room, room.name().code());
        }

        // Step 4 — DB guard (global invariant): target coordinate must be free of *other* rooms.
        RoomCommandGuard.assertCoordinateFree(roomRepository, candidate, room.location());

        // Step 5 — Domain mutation (recomputes name, records RoomRenamedEvent) then persist.
        String oldCode = room.name().code();
        room.changeCode(command.newCode());
        Room saved = roomRepository.save(room);
        return toResult(saved, oldCode);
    }

    private static RenameRoomCommand.@NonNull Result toResult(@NonNull Room room, String oldCode) {
        return new RenameRoomCommand.Result(
                room.id(),
                oldCode,
                room.name().code(),
                room.name().asString(),
                room.updatedAt());
    }
}
