package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to update the title and/or description of a workshop.
 * The title lock invariant (PUBLISHED + activeRegistrations > 0) and the
 * state guard (no CANCELLED) are enforced by the aggregate; the global
 * active-registration count is fetched by the Application handler via QueryBus.
 */
public record UpdateWorkshopInfoCommand(
        UUID workshopId,
        String newTitle,
        String newDescription
) implements Command<UpdateWorkshopInfoCommand.Result> {

    public record Result(UUID id, String title, String description, Instant updatedAt) {
    }
}