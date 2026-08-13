package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Auditor-initiated adjustment of an attendance record during the Reconciliation Window — the only
 * authoritative mutation of {@code currentResult} in that window (ADR 0019 §5). A justification is
 * mandatory.
 */
public record AuditorAdjustCommand(
        UUID recordId,
        AttendanceResult newStatus,
        String reason,
        String evidenceReference,
        Actor actor
) implements Command<AuditorAdjustCommand.Result> {

    public record Result(
            UUID recordId,
            int entryNumber,
            AttendanceResult currentResult,
            String state
    ) {
    }
}