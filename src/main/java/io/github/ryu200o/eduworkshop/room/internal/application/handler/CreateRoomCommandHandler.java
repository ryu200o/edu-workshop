package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for creating a room. Enforces Requirement 3 (multi-tier duplicate guard) in strict
 * performance order: cheap in-RAM local invariants first, then the single DB global check, then persist.
 *
 * <p>Package-private: only reachable through the module's {@code CommandBus}.</p>
 */
@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, CreateRoomCommand.Result> {

    private final RoomRepository roomRepository;

    CreateRoomCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public CreateRoomCommand.Result handle(CreateRoomCommand command) {
        // Step 1 — RAM guard (Local invariants): value objects self-validate & normalize, no IO.
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        RoomName name = RoomName.of(location, command.roomCode());

        // Step 2 — DB guard (Global invariant): single coordinate existence check.
        if (roomRepository.existsByCoordinate(location.building(), location.floor(), name.code())) {
            throw new DuplicateRoomException(name, location);
        }

        // Step 3 — Build the aggregate via the domain, then persist.
        Room room = Room.create(name, location, command.capacity());
        Room saved = roomRepository.save(room);
        return new CreateRoomCommand.Result(saved.id(), saved.name().asString());
    }
}
