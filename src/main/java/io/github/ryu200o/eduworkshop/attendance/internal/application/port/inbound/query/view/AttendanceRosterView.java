package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view;

import java.util.List;
import java.util.UUID;

/**
 * Roster summary for a workshop: the number of recorded learners ({@code total}) and the breakdown
 * by result ({@code present}/{@code late}/{@code absent}/{@code excused}), plus the master-only
 * records. {@code total} always equals the size of {@code records} and the sum of the breakdown.
 */
public record AttendanceRosterView(
        UUID workshopId,
        int total,
        int present,
        int late,
        int absent,
        int excused,
        List<AttendanceRosterEntryView> records
) {
}