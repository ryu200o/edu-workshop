package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

public record ChangeWorkshopRoomCommand(
        UUID workshopId,
        UUID newRoomId
) implements Command<ChangeWorkshopRoomCommand.Result> {

    public record Result(UUID id, UUID roomId, Instant updatedAt) {
    }
}
