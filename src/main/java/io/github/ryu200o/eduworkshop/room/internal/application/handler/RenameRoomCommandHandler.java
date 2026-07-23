package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.infrastructure.event.SpringDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;
    private final Clock clock;
    private final SpringDomainEventPublisher domainEventPublisher;

    RenameRoomCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy, Clock clock,
                             SpringDomainEventPublisher domainEventPublisher) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public RenameRoomCommand.Result handle(RenameRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomName newName = RoomName.of(command.newName());

        if (newName.equals(room.name())) {
            return toResult(room, room.name());
        }

        RoomName oldName = room.name();
        Instant now = Instant.now(clock);
        room.changeName(newName, uniquenessPolicy, now);
        Room saved = roomRepository.save(room);
        domainEventPublisher.publishEvents(room.recordedEvents());
        room.clearDomainEvents();
        return toResult(saved, oldName);
    }

    private static RenameRoomCommand.Result toResult(Room room, RoomName oldName) {
        return new RenameRoomCommand.Result(
                room.id().value(),
                oldName.value(),
                room.name().value(),
                room.updatedAt());
    }
}
