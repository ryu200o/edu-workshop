package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for renaming a room (changing its free-form {@code name}; building/floor/code unchanged).
 * The domain mutation records a {@link io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent}
 * so consumer modules can react. Name uniqueness is enforced by a fail-fast RAM check in the handler
 * (mirroring {@code uk_rooms_building_floor_name}) and, authoritatively, by the DB constraint plus the
 * race-proof gate in the write adapter. Package-private:
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

        // Step 4 — DB guard (global invariant): another room at this location must not already hold the
        //         candidate name (mirrors uk_rooms_building_floor_name). The DB constraint remains the
        //         authoritative race-proof gate; this is the fail-fast UX check.
        if (roomRepository.existsByName(room.location(), candidate)) {
            throw new DuplicateRoomException(candidate, room.location());
        }

        // Step 5 — Domain mutation (records RoomRenamedEvent) then persist.
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
