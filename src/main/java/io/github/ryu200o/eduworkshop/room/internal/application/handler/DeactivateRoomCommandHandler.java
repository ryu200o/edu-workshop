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

import java.time.Clock;
import java.time.Instant;

/**
 * Use-case handler for permanently deactivating a room. Loads the aggregate, delegates the (irreversible)
 * state transition to the domain (guard + idempotency owned there), then persists. Package-private:
 * reachable only via the module {@code CommandBus}.
 */
@Component
class DeactivateRoomCommandHandler implements CommandHandler<DeactivateRoomCommand, DeactivateRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final Clock clock;

    DeactivateRoomCommandHandler(RoomRepository roomRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DeactivateRoomCommand.Result handle(DeactivateRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomState previous = room.state();
        Instant now = Instant.now(clock);
        room.deactivate(now);
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
