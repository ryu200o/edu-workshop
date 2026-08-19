package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final RoomDomainEventPublisher roomDomainEventPublisher;

    CreateRoomCommandHandler(RoomRepository roomRepository, Clock clock,
                             RoomDomainEventPublisher roomDomainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
    }

    @Override
    @Transactional
    public void handle(CreateRoomCommand command) {
        RoomId id = RoomId.of(command.roomId());
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        RoomName name = RoomName.of(command.name());
        RoomCapacity capacity = RoomCapacity.of(command.capacity());
        RoomCode code = RoomCode.of(command.code());
        Instant now = Instant.now(clock);

        if (roomRepository.existsByCoordinate(location, code)) {
            throw new DuplicateRoomCodeException(location, code);
        }
        if (roomRepository.existsByName(location, name)) {
            throw new DuplicateRoomNameException(location, name);
        }

        Room room = Room.create(id, name, location, code, capacity, now);

        roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
    }
}
