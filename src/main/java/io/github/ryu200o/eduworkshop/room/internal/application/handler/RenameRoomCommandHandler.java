package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final RoomDomainEventPublisher roomDomainEventPublisher;

    RenameRoomCommandHandler(RoomRepository roomRepository, Clock clock,
                             RoomDomainEventPublisher roomDomainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
    }

    @Override
    @Transactional
    public void handle(RenameRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomName newName = RoomName.of(command.newName());

        if (newName.equals(room.name())) {
            return;
        }

        if (roomRepository.existsByName(room.location(), newName)) {
            throw new DuplicateRoomNameException(room.location(), newName);
        }

        Instant now = Instant.now(clock);
        room.changeName(newName, now);
        roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
    }
}
