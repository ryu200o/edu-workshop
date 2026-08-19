package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Command to release the room of a PLANNED workshop (PLANNED → DRAFT) by calling
 * {@code Workshop.returnToDraft} — the admin actively gives up the planned room.
 */
public record UnplanWorkshopCommand(
        UUID workshopId
) implements Command {
}
