package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to move a PUBLISHED workshop to a new time window (keeping the room and the student
 * registrations). The deadline and window validity are enforced by the aggregate; the global
 * conflict check is orchestrated here.
 */
public record RescheduleWorkshopCommand(
        UUID workshopId,
        Instant newStartTime,
        Instant newEndTime
) implements Command<RescheduleWorkshopCommand.Result> {

    public record Result(UUID id, Instant newStartTime, Instant newEndTime, Instant updatedAt) {
    }
}
