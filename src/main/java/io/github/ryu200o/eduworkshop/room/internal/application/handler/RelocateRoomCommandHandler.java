package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for relocating a room (changing its building/floor; code unchanged). The global
 * uniqueness invariant is enforced inside the aggregate through the injected domain
 * {@link RoomUniquenessPolicy}: because relocation preserves both code and name, the aggregate checks
 * BOTH the {@code (location, code)} and {@code (location, name)} pairs at the target via the policy.
 * Package-private: reachable only via the module {@code CommandBus}.
 */
@Component
class RelocateRoomCommandHandler implements CommandHandler<RelocateRoomCommand, RelocateRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    RelocateRoomCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
    }

    @Override
    @Transactional
    public RelocateRoomCommand.Result handle(RelocateRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException(command.roomId().toString()));

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new location.
        RoomLocation newLocation = RoomLocation.of(command.newBuilding(), command.newFloor());

        // Step 3 — Idempotency: same location ⇒ no change, no gate, no persist.
        //         Returns the current entity's updatedAt (NOT Instant.now()) — nothing changed.
        if (newLocation.equals(room.location())) {
            return toResult(room, room.location());
        }

        // Step 4 — Domain mutation (keeps name/code, records RoomRelocatedEvent). The aggregate enforces the
        //         (location, code) and (location, name) invariants via the policy before mutating; then persist.
        RoomLocation oldLocation = room.location();
        room.relocateTo(newLocation, uniquenessPolicy);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldLocation);
    }

    private static RelocateRoomCommand.Result toResult(Room room, RoomLocation oldLocation) {
        return new RelocateRoomCommand.Result(
                room.id().value(), oldLocation, room.location(), room.updatedAt());
    }
}
