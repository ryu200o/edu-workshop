package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetAttendanceLedgerQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAttendanceLedgerQueryHandlerTest {

    @Mock
    private AttendanceRecordReader attendanceRecordReader;

    @Test
    void returnsLedgerWhenRecordExists() {
        UUID recordId = UUID.randomUUID();
        AttendanceRecordLedgerView view = new AttendanceRecordLedgerView(recordId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, java.util.List.of(),
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T10:00:00Z"));
        when(attendanceRecordReader.getById(recordId)).thenReturn(Optional.of(view));

        AttendanceRecordLedgerView result = new GetAttendanceLedgerQueryHandler(attendanceRecordReader)
                .handle(new GetAttendanceLedgerQuery(recordId));

        assertThat(result).isEqualTo(view);
        verify(attendanceRecordReader).getById(recordId);
    }

    @Test
    void throwsWhenRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(attendanceRecordReader.getById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetAttendanceLedgerQueryHandler(attendanceRecordReader)
                .handle(new GetAttendanceLedgerQuery(recordId)))
                .isInstanceOf(AttendanceNotFoundException.class);
    }
}