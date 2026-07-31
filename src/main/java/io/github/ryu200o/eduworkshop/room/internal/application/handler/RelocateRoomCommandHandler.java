package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class RelocateRoomCommandHandler implements CommandHandler<RelocateRoomCommand, RelocateRoomCommand.Result> {

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
    public RelocateRoomCommand.Result handle(RelocateRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomLocation newLocation = RoomLocation.of(command.newBuilding(), command.newFloor());

        if (newLocation.equals(room.location())) {
            return toResult(room, room.location());
        }

        if (roomRepository.existsByCoordinate(newLocation, room.code())) {
            throw new DuplicateRoomCodeException(newLocation, room.code());
        }
        if (roomRepository.existsByName(newLocation, room.name())) {
            throw new DuplicateRoomNameException(newLocation, room.name());
        }

        RoomLocation oldLocation = room.location();
        Instant now = Instant.now(clock);
        room.relocateTo(newLocation, now);
        Room saved = roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
        return toResult(saved, oldLocation);
    }

    private static RelocateRoomCommand.Result toResult(Room room, RoomLocation oldLocation) {
        return new RelocateRoomCommand.Result(
                room.id().value(),
                new RelocateRoomCommand.LocationDto(oldLocation.building(), oldLocation.floor()),
                new RelocateRoomCommand.LocationDto(room.location().building(), room.location().floor()),
                room.updatedAt());
    }
}
