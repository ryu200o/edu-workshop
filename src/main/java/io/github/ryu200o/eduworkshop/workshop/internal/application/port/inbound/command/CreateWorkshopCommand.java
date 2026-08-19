package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to create a new workshop in DRAFT state. Raw input parameters only — all
 * validation/normalization is performed by the Workshop domain value objects inside the handler.
 * The {@code workshopId} is caller-generated (ADR 0021 Caller-Generated ID): the inbound adapter
 * assigns it and the handler persists the aggregate under that id.
 *
 * @param workshopId  the caller-generated aggregate id
 * @param title       the workshop title (non-blank; validated by {@code WorkshopTitle})
 * @param description the workshop description (nullable; validated by {@code WorkshopDescription})
 * @param startTime   the planned start time (must be before endTime; validated by aggregate)
 * @param endTime     the planned end time (must be after startTime; validated by aggregate)
 * @param capacity    the maximum participant count (positive; validated by {@code WorkshopCapacity})
 */
public record CreateWorkshopCommand(
        UUID workshopId,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        int capacity
) implements Command {
}
