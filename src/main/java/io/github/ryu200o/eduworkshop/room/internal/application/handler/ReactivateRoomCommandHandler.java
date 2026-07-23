package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ReactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.infrastructure.event.SpringDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class ReactivateRoomCommandHandler implements CommandHandler<ReactivateRoomCommand, ReactivateRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final SpringDomainEventPublisher domainEventPublisher;

    ReactivateRoomCommandHandler(RoomRepository roomRepository, Clock clock,
                                 SpringDomainEventPublisher domainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public ReactivateRoomCommand.Result handle(ReactivateRoomCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomState previous = room.state();
        Instant now = Instant.now(clock);
        room.reactivate(now);
        if (previous == room.state()) {
            return new ReactivateRoomCommand.Result(
                    room.id().value(), previous, room.state(), room.updatedAt());
        }
        Room saved = roomRepository.save(room);
        domainEventPublisher.publishEvents(room.recordedEvents());
        room.clearDomainEvents();
        return new ReactivateRoomCommand.Result(
                saved.id().value(), previous, saved.state(), saved.updatedAt());
    }
}
