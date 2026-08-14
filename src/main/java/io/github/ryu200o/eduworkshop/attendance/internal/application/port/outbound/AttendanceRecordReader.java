package io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side outbound port (SPI) for the Attendance read side. Consumer-Driven (ADR 0017): declares
 * only the lookups the query use cases need and returns {@code *View} projections directly
 * (CQRS bypass — no domain aggregate reconstruction). Implementations must be side-effect free and
 * push predicates into SQL.
 */
public interface AttendanceRecordReader {

    /**
     * Loads the full Decision Ledger of a single record (master + entries ordered by
     * {@code entry_number ASC}). Empty when the record does not exist.
     */
    Optional<AttendanceRecordLedgerView> getById(UUID recordId);

    /**
     * Builds the workshop roster: a summary (total + result breakdown) plus the master-only records.
     * Result/state predicates are pushed down to SQL. {@code total} is always consistent with the
     * returned {@code records} (and with the sum of the breakdown).
     *
     * @param workshopId   the workshop to roster
     * @param resultFilter optional {@link AttendanceResult} filter (null = all results)
     * @param stateFilter  optional {@link AttendanceState} filter (null = all states)
     */
    AttendanceRosterView getByWorkshopId(UUID workshopId, AttendanceResult resultFilter, AttendanceState stateFilter);
}