package io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to move a PUBLISHED workshop to a new time window (keeping the room and the student
 * registrations). The deadline and window validity are enforced by the aggregate; the global
 * conflict check is orchestrated here. Optional buffer parameters allow the Planner to renegotiate
 * the Occupancy Contract at PUBLISHED (ADR 0018 P4) — {@code null} keeps the current buffer.
 *
 * @param workshopId         the workshop to reschedule
 * @param newStartTime       the new start instant
 * @param newEndTime         the new end instant
 * @param bufferBeforeMinutes new buffer before (nullable → keep current)
 * @param bufferAfterMinutes  new buffer after (nullable → keep current)
 * @param justification      reason for the renegotiation (required — ADR 0018 P4)
 */
public record RescheduleWorkshopCommand(
        UUID workshopId,
        Instant newStartTime,
        Instant newEndTime,
        Integer bufferBeforeMinutes,
        Integer bufferAfterMinutes,
        String justification
) implements Command<RescheduleWorkshopCommand.Result> {

    public record Result(UUID id, Instant newStartTime, Instant newEndTime, Instant updatedAt) {
    }
}
