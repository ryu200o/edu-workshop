package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.infrastructure.event.SpringDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class ChangeRoomCodeCommandHandler implements CommandHandler<ChangeRoomCodeCommand, ChangeRoomCodeCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;
    private final Clock clock;
    private final SpringDomainEventPublisher domainEventPublisher;

    ChangeRoomCodeCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy, Clock clock,
                                 SpringDomainEventPublisher domainEventPublisher) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public ChangeRoomCodeCommand.Result handle(ChangeRoomCodeCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomCode newCode = RoomCode.of(command.newCode());

        if (newCode.equals(room.code())) {
            return toResult(room, room.code());
        }

        RoomCode oldCode = room.code();
        Instant now = Instant.now(clock);
        room.changeCode(newCode, uniquenessPolicy, now);
        Room saved = roomRepository.save(room);
        domainEventPublisher.publishEvents(room.recordedEvents());
        room.clearDomainEvents();
        return toResult(saved, oldCode);
    }

    private static ChangeRoomCodeCommand.Result toResult(Room room, RoomCode oldCode) {
        return new ChangeRoomCodeCommand.Result(
                room.id().value(), oldCode.value(), room.code().value(), room.updatedAt());
    }
}
