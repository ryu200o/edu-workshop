package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import io.github.ryu200o.eduworkshop.shared.cqs.CommandHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use-case handler for renaming a room (changing its code; building/floor unchanged). Enforces the
 * multi-tier duplicate guard in performance order: load (write side) → RAM (VO self-validation) →
 * DB (target-coordinate uniqueness) → domain mutation → persist. Package-private: reachable only via
 * the module {@code CommandBus}.
 */
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RoomResponse> {

    private final RoomStateGateway roomStateGateway;
    private final RoomExistencePort roomExistencePort;

    RenameRoomCommandHandler(RoomStateGateway roomStateGateway, RoomExistencePort roomExistencePort) {
        this.roomStateGateway = roomStateGateway;
        this.roomExistencePort = roomExistencePort;
    }

    @Override
    @Transactional
    public RoomResponse handle(@NonNull RenameRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomStateGateway.loadById(command.roomId())
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new code.
        RoomName candidate = RoomName.of(room.location(), command.newCode());

        // Step 3 — Idempotency: same code ⇒ no coordinate change, no gate, no persist.
        if (candidate.code().equals(room.name().code())) {
            return toResponse(room);
        }

        // Step 4 — DB guard (global invariant): target coordinate must be free of *other* rooms.
        if (roomExistencePort.existsByBuildingAndFloorAndCode(
                room.location().building(), room.location().floor(), candidate.code())) {
            throw new DuplicateRoomException(candidate, room.location());
        }

        // Step 5 — Domain mutation (recomputes name, records RoomRenamedEvent) then persist.
        room.changeCode(command.newCode());
        Room saved = roomStateGateway.save(room);
        return toResponse(saved);
    }

    private static RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.id(),
                room.name().asString(),
                room.location().building(),
                room.location().floor(),
                room.capacity(),
                room.state().name());
    }
}
