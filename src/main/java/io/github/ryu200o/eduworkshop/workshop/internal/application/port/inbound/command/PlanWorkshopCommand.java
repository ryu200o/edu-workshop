package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

public record PlanWorkshopCommand(
        UUID workshopId,
        UUID roomId
) implements Command {
}
