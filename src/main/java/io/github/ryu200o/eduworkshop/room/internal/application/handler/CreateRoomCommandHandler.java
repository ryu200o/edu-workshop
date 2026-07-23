package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.*;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.infrastructure.event.SpringDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, CreateRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;
    private final Clock clock;
    private final SpringDomainEventPublisher domainEventPublisher;

    CreateRoomCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy, Clock clock,
                             SpringDomainEventPublisher domainEventPublisher) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public CreateRoomCommand.Result handle(CreateRoomCommand command) {
        RoomId id = RoomId.generate();
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        RoomName name = RoomName.of(command.name());
        RoomCapacity capacity = RoomCapacity.of(command.capacity());
        RoomCode code = RoomCode.of(command.code());
        Instant now = Instant.now(clock);

        Room room = Room.create(id, name, location, code, capacity, now, uniquenessPolicy);

        Room saved = roomRepository.save(room);
        domainEventPublisher.publishEvents(room.recordedEvents());
        room.clearDomainEvents();
        return new CreateRoomCommand.Result(saved.id().value(), saved.name().value());
    }
}
