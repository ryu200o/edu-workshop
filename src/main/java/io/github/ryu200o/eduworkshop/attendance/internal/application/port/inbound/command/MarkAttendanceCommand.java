package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marks (or corrects) attendance for a batch of learners of an {@code IN_PROGRESS} workshop, while
 * the trainer is recording the roster.
 *
 * <p><strong>Batch = Batch of Attendance Modifications (Domain Discovery Round 2):</strong> the
 * trainer sends only the learners being acted on, never the whole roster. As an <em>input
 * invariant</em> of the command (not an Aggregate rule), a {@code studentId} must appear at most
 * once within {@code items} — a duplicate would silently record two entries for the same learner.
 * Enforced in the compact constructor: violation → {@link IllegalArgumentException} → HTTP 400.</p>
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
) implements Command {

    public MarkAttendanceCommand {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null.");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor must not be null.");
        }
        if (items == null) {
            throw new IllegalArgumentException("items must not be null.");
        }
        Set<UUID> seen = new HashSet<>();
        for (MarkItem item : items) {
            if (item == null || item.studentId() == null) {
                throw new IllegalArgumentException("Each mark item must have a studentId.");
            }
            if (!seen.add(item.studentId())) {
                throw new IllegalArgumentException(
                        "Duplicate studentId in mark command: " + item.studentId());
            }
        }
    }

    public record MarkItem(
            UUID studentId,
            AttendanceResult status,
            String note
    ) {
    }
}