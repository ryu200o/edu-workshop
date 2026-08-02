package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to update the time window of a pre-publish workshop (DRAFT or PLANNED only).
 * The state guard and time-window validity are enforced by the aggregate.
 */
public record UpdateWorkshopScheduleCommand(
        UUID workshopId,
        Instant newStartTime,
        Instant newEndTime
) implements Command<UpdateWorkshopScheduleCommand.Result> {

    public record Result(UUID id, Instant startTime, Instant endTime, Instant updatedAt) {
    }
}