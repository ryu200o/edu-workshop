package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.List;
import java.util.UUID;

/**
 * Marks (or corrects) attendance for a batch of learners of an {@code IN_PROGRESS} workshop, while
 * the trainer is recording the roster.
 *
 * <p>Global gates orchestrated by the handler (ADR 0005): the workshop must exist and be
 * {@code IN_PROGRESS} (OQ-5), the actor must be a {@code TRAINER}, and — fail-fast over the whole
 * batch — every learner must have a {@code VERIFIED} registration (SA directive). The batch is
 * atomic: if any learner is not verified, nothing is processed.</p>
 */
public record MarkAttendanceCommand(
        UUID workshopId,
        List<MarkItem> items,
        Actor actor
) implements Command<MarkAttendanceCommand.Result> {

    public record MarkItem(
            UUID studentId,
            AttendanceResult status,
            String note
    ) {
    }

    public record Result(int processedCount) {
    }
}