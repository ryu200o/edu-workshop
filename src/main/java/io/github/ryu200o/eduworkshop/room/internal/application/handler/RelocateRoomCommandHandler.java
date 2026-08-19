package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class RelocateRoomCommandHandler implements CommandHandler<RelocateRoomCommand> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final RoomDomainEventPublisher roomDomainEventPublisher;

    RelocateRoomCommandHandler(RoomRepository roomRepository, Clock clock,
                                RoomDomainEventPublisher roomDomainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
    }

    @Override
    @Transactional
    public void handle(RelocateRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomLocation newLocation = RoomLocation.of(command.newBuilding(), command.newFloor());

        if (newLocation.equals(room.location())) {
            return;
        }

        if (roomRepository.existsByCoordinate(newLocation, room.code())) {
            throw new DuplicateRoomCodeException(newLocation, room.code());
        }
        if (roomRepository.existsByName(newLocation, room.name())) {
            throw new DuplicateRoomNameException(newLocation, room.name());
        }

        Instant now = Instant.now(clock);
        room.relocateTo(newLocation, now);
        roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
    }
}
