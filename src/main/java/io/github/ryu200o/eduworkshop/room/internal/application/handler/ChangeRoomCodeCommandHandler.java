package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.policy.RoomUniquenessPolicy;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case handler for changing a room's independent {@code code} (an int used only for FE ordering).
 * This is a silent mutation — the domain emits NO event. The {@code (building, floor, code)} uniqueness
 * invariant is enforced inside the aggregate through the injected domain {@link RoomUniquenessPolicy};
 * the DB constraint plus the race-proof gate in the write adapter remain the authoritative authority.
 * Package-private: reachable only via the module {@code CommandBus}.
 *
 * <p>Per the VO-purity standard (ADR 0009) the handler only builds the {@link RoomCode} value object
 * (which self-validates) and delegates to the aggregate; it performs no business-rule checks of its own.</p>
 */
@Component
class ChangeRoomCodeCommandHandler implements CommandHandler<ChangeRoomCodeCommand, ChangeRoomCodeCommand.Result> {

    private final RoomRepository roomRepository;
    private final RoomUniquenessPolicy uniquenessPolicy;

    ChangeRoomCodeCommandHandler(RoomRepository roomRepository, RoomUniquenessPolicy uniquenessPolicy) {
        this.roomRepository = roomRepository;
        this.uniquenessPolicy = uniquenessPolicy;
    }

    @Override
    @Transactional
    public ChangeRoomCodeCommand.Result handle(ChangeRoomCodeCommand command) {
        // Step 1 — Load the aggregate (write side).
        Room room = roomRepository.loadById(RoomId.of(command.roomId()))
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        // Step 2 — Build the value object. The RoomCode VO self-validates (e.g. rejects non-positive);
        //         there is no separate RAM guard here — the Application layer only constructs VOs.
        RoomCode newCode = RoomCode.of(command.newCode());

        // Step 3 — Idempotency: same code ⇒ no change, no gate, no persist.
        if (newCode.equals(room.code())) {
            return toResult(room, room.code().value());
        }

        // Step 4 — Domain mutation (silent, no event). The aggregate enforces the (location, code) uniqueness
        //         invariant via the policy before mutating; then persist.
        int oldCode = room.code().value();
        room.changeCode(newCode, uniquenessPolicy);
        Room saved = roomRepository.save(room);
        return toResult(saved, oldCode);
    }

    private static ChangeRoomCodeCommand.Result toResult(Room room, int oldCode) {
        return new ChangeRoomCodeCommand.Result(
                room.id().value(), oldCode, room.code().value(), room.updatedAt());
    }
}
