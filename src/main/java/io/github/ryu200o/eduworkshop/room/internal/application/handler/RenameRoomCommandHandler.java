package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for renaming a room (changing its free-form {@code name}; building/floor/code unchanged).
 * The domain mutation records a {@link io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent}
 * so consumer modules can react. Name uniqueness is enforced by the DB {@code uk_rooms_building_floor_name}
 * constraint and the race-proof gate in the write adapter; the handler does not pre-check it. Package-private:
 * reachable only via the module {@code CommandBus}.
 */
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {

    private final RoomRepository roomRepository;

    RenameRoomCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public RenameRoomCommand.Result handle(RenameRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new name.
        RoomName candidate = RoomName.of(command.newName());

        // Step 3 — Idempotency: same name ⇒ no change, no gate, no persist.
        if (candidate.equals(room.name())) {
            return toResult(room, room.name().asString());
        }

        // Step 4 — Domain mutation (records RoomRenamedEvent) then persist.
        // Name uniqueness is enforced by the DB constraint + write-adapter race gate.
        String oldName = room.name().asString();
        room.changeName(command.newName());
        Room saved = roomRepository.save(room);
        return toResult(saved, oldName);
    }

    private static RenameRoomCommand.Result toResult(Room room, String oldName) {
        return new RenameRoomCommand.Result(
                room.id().value(),
                oldName,
                room.name().asString(),
                room.updatedAt());
    }
}
