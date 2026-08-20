package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class ChangeRoomCodeCommandHandler implements CommandHandler<ChangeRoomCodeCommand> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final RoomDomainEventPublisher roomDomainEventPublisher;

    ChangeRoomCodeCommandHandler(RoomRepository roomRepository, Clock clock,
                                  RoomDomainEventPublisher roomDomainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
    }

    @Override
    @Transactional
    public void handle(ChangeRoomCodeCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomCode newCode = RoomCode.of(command.newCode());

        if (newCode.equals(room.code())) {
            return;
        }

        if (roomRepository.existsByCoordinate(room.location(), newCode)) {
            throw new DuplicateRoomCodeException(room.location(), newCode);
        }

        Instant now = Instant.now(clock);
        room.changeCode(newCode, now);
        roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
    }
}
