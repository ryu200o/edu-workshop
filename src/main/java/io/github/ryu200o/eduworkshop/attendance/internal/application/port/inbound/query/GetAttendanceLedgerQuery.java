package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Query for the full Decision Ledger of a single attendance record (master + entries ordered by
 * {@code entryNumber} ascending) — the audit trail per ADR 0019 §6.
 */
public record GetAttendanceLedgerQuery(
        UUID recordId
) implements Query<AttendanceRecordLedgerView> {
}