package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Books a seat for a student on a workshop. The student identity is a logical reference (no User
 * module — SA+PO decision) and comes from the {@code AuthenticatedPrincipal} at the HTTP boundary.
 * The {@code registrationId} is caller-generated (ADR 0021 Caller-Generated ID): the inbound adapter
 * assigns it and the handler persists the aggregate under that id.
 */
public record RegisterWorkshopCommand(
        UUID registrationId,
        UUID workshopId,
        UUID userId
) implements Command {
}
