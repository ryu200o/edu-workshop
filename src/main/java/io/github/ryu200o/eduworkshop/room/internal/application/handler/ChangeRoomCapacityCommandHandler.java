package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class ChangeRoomCapacityCommandHandler implements CommandHandler<ChangeRoomCapacityCommand> {

    private final RoomRepository roomRepository;
    private final Clock clock;
    private final RoomDomainEventPublisher roomDomainEventPublisher;

    ChangeRoomCapacityCommandHandler(RoomRepository roomRepository, Clock clock,
                                      RoomDomainEventPublisher roomDomainEventPublisher) {
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
    }

    @Override
    @Transactional
    public void handle(ChangeRoomCapacityCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomCapacity newCapacity = RoomCapacity.of(command.newCapacity());
        if (newCapacity.equals(room.capacity())) {
            return;
        }

        Instant now = Instant.now(clock);
        room.changeCapacity(newCapacity, now);
        roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
    }
}
