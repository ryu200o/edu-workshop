package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomDomainEventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class ChangeRoomCapacityCommandHandler implements CommandHandler<ChangeRoomCapacityCommand, ChangeRoomCapacityCommand.Result> {

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
    public ChangeRoomCapacityCommand.Result handle(ChangeRoomCapacityCommand command) {
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        RoomCapacity newCapacity = RoomCapacity.of(command.newCapacity());
        if (newCapacity.equals(room.capacity())) {
            return toResult(room, room.capacity());
        }

        RoomCapacity oldCapacity = room.capacity();
        Instant now = Instant.now(clock);
        room.changeCapacity(newCapacity, now);
        Room saved = roomRepository.save(room);
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();
        return toResult(saved, oldCapacity);
    }

    private static ChangeRoomCapacityCommand.Result toResult(Room room, RoomCapacity oldCapacity) {
        return new ChangeRoomCapacityCommand.Result(
                room.id().value(), oldCapacity.value(), room.capacity().value(), room.updatedAt());
    }
}
