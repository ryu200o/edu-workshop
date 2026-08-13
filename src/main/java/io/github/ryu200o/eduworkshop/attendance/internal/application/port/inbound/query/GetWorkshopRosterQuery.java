package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Query for the workshop attendance roster — the master-only records plus the result breakdown.
 * The optional {@code result} filter mirrors the HTTP {@code ?status=} parameter
 * ({@code ALL|PRESENT|LATE|ABSENT|EXCUSED}); {@code null} means "no filter — whole roster".
 */
public record GetWorkshopRosterQuery(
        UUID workshopId,
        AttendanceResult result
) implements Query<AttendanceRosterView> {
}