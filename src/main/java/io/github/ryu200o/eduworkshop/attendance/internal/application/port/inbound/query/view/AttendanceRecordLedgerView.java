package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read projection of a single attendance record — the master plus its full Decision Ledger, ordered
 * by {@code entryNumber} ascending. Backed by a self-contained read (no cross-module JOIN).
 */
public record AttendanceRecordLedgerView(
        UUID recordId,
        UUID studentId,
        UUID workshopId,
        AttendanceResult currentResult,
        AttendanceState state,
        Instant reconciliationStartedAt,
        List<AttendanceLedgerEntryView> entries,
        Instant createdAt,
        Instant updatedAt
) {
}