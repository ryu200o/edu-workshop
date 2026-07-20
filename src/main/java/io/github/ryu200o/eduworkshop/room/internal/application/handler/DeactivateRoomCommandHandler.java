package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.DeactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for permanently deactivating a room. Loads the aggregate, delegates the (irreversible)
 * state transition to the domain (guard + idempotency owned there), then persists. Package-private:
 * reachable only via the module {@code CommandBus}.
 */
@Component
class DeactivateRoomCommandHandler implements CommandHandler<DeactivateRoomCommand, DeactivateRoomCommand.Result> {

    private final RoomRepository roomRepository;

    DeactivateRoomCommandHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public DeactivateRoomCommand.Result handle(DeactivateRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomState previous = room.state();
        room.deactivate();
        // Idempotency: a no-op transition (already DEACTIVATED) does not touch the DB; return the entity's
        // existing updatedAt (NOT Instant.now()) to avoid a false "change" timestamp.
        if (previous == room.state()) {
            return new DeactivateRoomCommand.Result(
                    room.id().value(), previous, room.state(), room.updatedAt());
        }
        Room saved = roomRepository.save(room);
        return new DeactivateRoomCommand.Result(
                saved.id().value(), previous, saved.state(), saved.updatedAt());
    }
}
