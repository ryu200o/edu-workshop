package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetWorkshopRosterQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for the workshop attendance roster. Pure read orchestration (CQRS): delegates to
 * {@link AttendanceRecordReader#getByWorkshopId}, which pushes the whole query (workshop + optional
 * result filter) down to SQL. A workshop with no records simply yields an empty roster (HTTP 200).
 */
@Component
class GetWorkshopRosterQueryHandler implements QueryHandler<GetWorkshopRosterQuery, AttendanceRosterView> {

    private final AttendanceRecordReader attendanceRecordReader;

    GetWorkshopRosterQueryHandler(AttendanceRecordReader attendanceRecordReader) {
        this.attendanceRecordReader = attendanceRecordReader;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceRosterView handle(GetWorkshopRosterQuery query) {
        return attendanceRecordReader.getByWorkshopId(query.workshopId(), query.result(), null);
    }
}