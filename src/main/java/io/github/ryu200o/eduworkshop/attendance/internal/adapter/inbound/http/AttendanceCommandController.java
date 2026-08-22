package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.AuditorAdjustCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.MarkAttendanceCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SelfCheckInCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SubmitAppealCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanAuditAttendance;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanMarkAttendance;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Attendance WRITE side (Command). Talks exclusively to the shared
 * {@link CommandBus}. The acting actor is derived from the {@link AuthenticatedPrincipal} (OQ-2 /
 * ADR 0019 §8): contextual authority is evaluated here against the global RBAC roles — a trainer
 * action ({@code mark}) is allowed for {@code PLANNER}/{@code ADMIN}, an auditor action
 * ({@code adjust}) only for {@code AUDITOR}, while self-service actions ({@code check-in}/{@code
 * appeal}) map any principal to {@code STUDENT} (eligibility is then proven by ownership of a
 * verified seat). The {@code X-Actor-Role}/{@code X-User-Id} headers are gone — never the source of
 * truth. Error handling is centralized in {@link AttendanceExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1")
class AttendanceCommandController {

    private final CommandBus commandBus;

    AttendanceCommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @CanMarkAttendance
    @PostMapping("/workshops/{workshopId}/attendance/mark")
    ResponseEntity<Void> markAttendance(@PathVariable UUID workshopId,
                                        @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @RequestBody MarkAttendanceRequest request) {
        var command = new MarkAttendanceCommand(workshopId, request.items().stream()
                .map(item -> new MarkAttendanceCommand.MarkItem(item.studentId(), item.status(), item.note()))
                .toList(), principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @Idempotent
    @PostMapping("/workshops/{workshopId}/attendance/check-in")
    ResponseEntity<Void> selfCheckIn(@PathVariable UUID workshopId,
                                     @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestBody SelfCheckInRequest request) {
        // Thin QR seam (Epic 3B, Slice A): the qrReference is opaque input captured here — real QR
        // resolution is Slice B (OQ-3B-1/2, backlog). The workshop candidate is the path workshopId,
        // the student comes from the authenticated principal; the handler never sees the QR itself.
        // Any authenticated principal is mapped to STUDENT by the handler (eligibility = verified
        // seat, proven downstream) — no contextual role is assigned here.
        var command = new SelfCheckInCommand(workshopId, request.qrReference(), principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @Idempotent
    @PostMapping("/attendance-records/{recordId}/appeal")
    ResponseEntity<Void> submitAppeal(@PathVariable UUID recordId,
                                      @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestBody SubmitAppealRequest request) {
        var command = new SubmitAppealCommand(recordId, request.reason(), request.evidenceReference(),
                principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @CanAuditAttendance
    @PostMapping("/attendance-records/{recordId}/adjust")
    ResponseEntity<Void> auditorAdjust(@PathVariable UUID recordId,
                                       @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody AuditorAdjustRequest request) {
        var command = new AuditorAdjustCommand(recordId, request.newStatus(), request.reason(),
                request.evidenceReference(), principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @CanAuditAttendance
    @Idempotent
    @PostMapping("/workshops/{workshopId}/attendance/finalize")
    ResponseEntity<Void> finalizeRoster(@PathVariable UUID workshopId,
                                        @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        // Manual dual-trigger for roster finalization (ADR 0019 §4). The AUDITOR actor is assigned
        // by the handler; the system scheduler uses the SYSTEM actor via the auto-finalize job.
        var command = new FinalizeWorkshopRosterCommand(workshopId, principal.userId());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    record MarkAttendanceRequest(List<MarkItemRequest> items) {
        record MarkItemRequest(UUID studentId, AttendanceResult status, String note) {
        }
    }

    record SubmitAppealRequest(String reason, String evidenceReference) {
    }

    record AuditorAdjustRequest(AttendanceResult newStatus, String reason, String evidenceReference) {
    }

    record SelfCheckInRequest(String qrReference) {
    }
}