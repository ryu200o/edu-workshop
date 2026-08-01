package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

public record PublishWorkshopCommand(
        UUID workshopId
) implements Command<PublishWorkshopCommand.Result> {

    public record Result(UUID id, Instant updatedAt) {
    }
}
