package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Write command to create a new workshop in DRAFT state. Raw input parameters only — all
 * validation/normalization is performed by the Workshop domain value objects inside the handler.
 *
 * @param title       the workshop title (non-blank; validated by {@code WorkshopTitle})
 * @param description the workshop description (nullable; validated by {@code WorkshopDescription})
 * @param startTime   the planned start time (must be before endTime; validated by aggregate)
 * @param endTime     the planned end time (must be after startTime; validated by aggregate)
 * @param capacity    the maximum participant count (positive; validated by {@code WorkshopCapacity})
 */
public record CreateWorkshopCommand(
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        int capacity
) implements Command<CreateWorkshopCommand.Result> {

    /**
     * Lightweight write-side result — carries only the fields directly affected by the creation
     * (the new workshop's id and title) to keep the write flow minimal.
     *
     * @param id    the id minted for the created workshop
     * @param title the workshop title
     */
    public record Result(UUID id, String title) {
    }
}
