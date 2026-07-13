package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RoomCreatedResult;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use-case handler for creating a room. Enforces Requirement 3 (multi-tier duplicate guard) in strict
 * performance order: cheap in-RAM local invariants first, then the single DB global check, then persist.
 *
 * <p>Package-private: only reachable through the module's {@code CommandBus}.</p>
 */
@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, RoomCreatedResult> {

    private final RoomExistencePort roomExistencePort;
    private final RoomStateGateway roomStateGateway;

    CreateRoomCommandHandler(RoomExistencePort roomExistencePort, RoomStateGateway roomStateGateway) {
        this.roomExistencePort = roomExistencePort;
        this.roomStateGateway = roomStateGateway;
    }

    @Override
    @Transactional
    public RoomCreatedResult handle(@NonNull CreateRoomCommand command) {
        // Step 1 — RAM guard (Local invariants): value objects self-validate & normalize, no IO.
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        RoomName name = RoomName.of(location, command.roomCode());

        // Step 2 — DB guard (Global invariant): single existence check.
        if (roomExistencePort.existsByNameAndLocation(name, location)) {
            throw new DuplicateRoomException(name, location);
        }

        // Step 3 — Build the aggregate via the domain, then persist.
        Room room = Room.create(name, location, command.capacity());
        Room saved = roomStateGateway.save(room);
        return new RoomCreatedResult(saved.id(), saved.name().asString());
    }
}
