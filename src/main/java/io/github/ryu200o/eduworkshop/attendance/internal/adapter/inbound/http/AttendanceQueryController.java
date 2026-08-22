package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetAttendanceLedgerQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.GetWorkshopRosterQuery;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanViewAttendance;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driving HTTP adapter for the Attendance READ side (Query). Talks exclusively to the shared
 * {@link QueryBus}. Error handling is centralized in {@link AttendanceExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1")
class AttendanceQueryController {

    private final QueryBus queryBus;

    AttendanceQueryController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @CanViewAttendance
    @GetMapping("/workshops/{workshopId}/attendance")
    ResponseEntity<AttendanceRosterView> workshopRoster(@PathVariable UUID workshopId,
                                                        @RequestParam(value = "status", required = false) AttendanceResult status) {
        return ResponseEntity.ok(queryBus.execute(new GetWorkshopRosterQuery(workshopId, status)));
    }

    @PreAuthorize("hasAnyRole(" +
            "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).USER, " +
            "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).AUDITOR, " +
            "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).ADMIN)")
    @GetMapping("/attendance-records/{recordId}")
    ResponseEntity<AttendanceRecordLedgerView> ledger(@PathVariable UUID recordId) {
        return ResponseEntity.ok(queryBus.execute(new GetAttendanceLedgerQuery(recordId)));
    }
}