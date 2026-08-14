package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

import java.time.Instant;
import java.util.UUID;

/**
 * Master-only roster row projection (no ledger entries) — enough for the roster list and the result
 * breakdown. Task-tailored per ADR 0017: the roster UI needs current result + state, not the audit
 * trail.
 */
public record AttendanceRosterEntryView(
        UUID recordId,
        UUID studentId,
        AttendanceResult currentResult,
        AttendanceState state,
        Instant updatedAt
) {
}