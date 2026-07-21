package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for renaming a room (changing its free-form {@code name}; building/floor/code unchanged).
 * The domain mutation records a {@link io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent}
 * so consumer modules can react. The global {@code (location, name)} uniqueness invariant is enforced inside
 * the aggregate through the injected domain {@link RoomUniquenessPolicy}; the DB constraint plus the race-proof
 * gate in the write adapter remain the authoritative authority. Package-private:
 * reachable only via the module {@code CommandBus}.
 */
@Component
class RenameRoomCommandHandler implements CommandHandler<RenameRoomCommand, RenameRoomCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    RenameRoomCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
    }

    @Override
    @Transactional
    public RenameRoomCommand.Result handle(RenameRoomCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        // Step 2 — RAM guard (local invariant): the VO validates/normalizes the new name.
        RoomName newName = RoomName.of(command.newName());

        // Step 3 — Idempotency: same name ⇒ no change, no gate, no persist.
        if (newName.equals(room.name())) {
            return toResult(room, room.name());
        }

        // Step 4 — Domain mutation (records RoomRenamedEvent). The aggregate enforces the (location, name)
        //         uniqueness invariant via the policy before mutating; then persist.
        RoomName oldName = room.name();
        room.changeName(newName, uniquenessPolicy);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldName);
    }

    private static RenameRoomCommand.Result toResult(Room room,RoomName oldName) {
        return new RenameRoomCommand.Result(
                room.id().value(),
                oldName.value(),
                room.name().value(),
                room.updatedAt());
    }
}
