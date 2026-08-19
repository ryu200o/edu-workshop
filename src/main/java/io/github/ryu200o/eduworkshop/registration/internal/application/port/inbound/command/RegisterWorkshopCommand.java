package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Books a seat for a student on a workshop. The student identity is a logical reference (no User
 * module — SA+PO decision) and comes from the {@code AuthenticatedPrincipal} at the HTTP boundary.
 */
public record RegisterWorkshopCommand(
        UUID workshopId,
        UUID userId
) implements Command<RegisterWorkshopCommand.Result> {

    public record Result(UUID registrationId, Instant registeredAt) {
    }
}
