package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetWorkshopRosterQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterEntryView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkshopRosterQueryHandlerTest {

    @Mock
    private AttendanceRecordReader attendanceRecordReader;

    @Test
    void delegatesToReaderAndPassesFilterThrough() {
        UUID workshopId = UUID.randomUUID();
        AttendanceRosterEntryView row = new AttendanceRosterEntryView(UUID.randomUUID(), UUID.randomUUID(),
                AttendanceResult.PRESENT, AttendanceState.OPEN, Instant.parse("2026-09-01T10:00:00Z"));
        AttendanceRosterView view = new AttendanceRosterView(workshopId, 1, 1, 0, 0, 0, List.of(row));
        when(attendanceRecordReader.getByWorkshopId(workshopId, AttendanceResult.PRESENT, null))
                .thenReturn(view);

        AttendanceRosterView result = new GetWorkshopRosterQueryHandler(attendanceRecordReader)
                .handle(new GetWorkshopRosterQuery(workshopId, AttendanceResult.PRESENT));

        assertThat(result).isEqualTo(view);
        verify(attendanceRecordReader).getByWorkshopId(workshopId, AttendanceResult.PRESENT, null);
    }
}