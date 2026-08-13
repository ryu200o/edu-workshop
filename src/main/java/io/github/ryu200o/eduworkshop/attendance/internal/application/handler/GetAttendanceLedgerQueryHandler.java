package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetAttendanceLedgerQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for a single attendance record's full Decision Ledger (master + entries in
 * {@code entry_number} order). Pure read orchestration (CQRS) via
 * {@link AttendanceRecordReader#getById}; 404 when the record does not exist.
 */
@Component
class GetAttendanceLedgerQueryHandler implements QueryHandler<GetAttendanceLedgerQuery, AttendanceRecordLedgerView> {

    private final AttendanceRecordReader attendanceRecordReader;

    GetAttendanceLedgerQueryHandler(AttendanceRecordReader attendanceRecordReader) {
        this.attendanceRecordReader = attendanceRecordReader;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceRecordLedgerView handle(GetAttendanceLedgerQuery query) {
        return attendanceRecordReader.getById(query.recordId())
                .orElseThrow(() -> new AttendanceNotFoundException(query.recordId()));
    }
}