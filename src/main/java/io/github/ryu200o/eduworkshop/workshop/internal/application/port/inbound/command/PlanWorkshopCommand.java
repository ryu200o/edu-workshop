package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

public record PlanWorkshopCommand(
        UUID workshopId,
        UUID roomId
) implements Command<PlanWorkshopCommand.Result> {

    public record Result(UUID id, UUID roomId, Instant updatedAt, boolean hasRoomWarning) {
    }
}
