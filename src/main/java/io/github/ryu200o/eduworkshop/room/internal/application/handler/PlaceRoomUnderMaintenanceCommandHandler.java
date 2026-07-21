package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.PlaceRoomUnderMaintenanceCommand;
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
 * Use-case handler for placing a room under maintenance. Loads the aggregate, delegates the state
 * transition to the domain (guard + idempotency owned there), then persists. Package-private: reachable
 * only via the module {@code CommandBus}.
 */
@Component
class PlaceRoomUnderMaintenanceCommandHandler implements CommandHandler<PlaceRoomUnderMaintenanceCommand, PlaceRoomUnderMaintenanceCommand.Result> {

    private final RoomRepository roomRepository;
    private final Clock clock;

    PlaceRoomUnderMaintenanceCommandHandler(RoomRepository roomRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PlaceRoomUnderMaintenanceCommand.Result handle(PlaceRoomUnderMaintenanceCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomState previous = room.state();
        Instant now = Instant.now(clock);
        room.placeUnderMaintenance(now);
        // Idempotency: a no-op transition (same state) does not touch the DB; return the entity's
        // existing updatedAt (NOT Instant.now()) to avoid a false "change" timestamp.
        if (previous == room.state()) {
            return new PlaceRoomUnderMaintenanceCommand.Result(
                    room.id().value(), previous, room.state(), room.updatedAt());
        }
        Room saved = roomRepository.save(room);
        return new PlaceRoomUnderMaintenanceCommand.Result(
                saved.id().value(), previous, saved.state(), saved.updatedAt());
    }
}
