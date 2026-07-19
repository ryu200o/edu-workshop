package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for creating a room. The global-uniqueness invariant (no two rooms share the same
 * {@code (building, floor, code)} or {@code (building, floor, name)}) is enforced inside the aggregate
 * through the injected domain {@link RoomUniquenessPolicy}; the handler only orchestrates VO construction,
 * delegation and persistence.
 *
 * <p>Package-private: only reachable through the module's {@code CommandBus}.</p>
 */
@Component
class CreateRoomCommandHandler implements CommandHandler<CreateRoomCommand, CreateRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    CreateRoomCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
    }

    @Override
    @Transactional
    public CreateRoomCommand.Result handle(CreateRoomCommand command) {
        // Step 1 — RAM guard (Local invariants): value objects self-validate & normalize, no IO.
        RoomLocation location = RoomLocation.of(command.building(), command.floor());
        RoomName name = RoomName.of(command.name());

        if (command.code() <= 0) {
            throw new RoomDomainException("Room code must be greater than zero.");
        }

        // Step 2 — Domain owns the global invariant: the aggregate enforces uniqueness via the policy.
        Room room = Room.create(name, location, command.code(), command.capacity(), uniquenessPolicy);

        // Step 3 — Persist.
        Room saved = roomRepository.save(room);
        return new CreateRoomCommand.Result(saved.id().value(), saved.name().asString());
    }
}
