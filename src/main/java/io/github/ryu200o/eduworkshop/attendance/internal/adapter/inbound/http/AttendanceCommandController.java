package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.AuditorAdjustCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.MarkAttendanceCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SelfCheckInCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SubmitAppealCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;
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

    @PostMapping("/workshops/{workshopId}/attendance/mark")
    ResponseEntity<Void> markAttendance(@PathVariable UUID workshopId,
                                        @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @RequestBody MarkAttendanceRequest request) {
        Actor actor = trainerActor(principal);
        var command = new MarkAttendanceCommand(workshopId, request.items().stream()
                .map(item -> new MarkAttendanceCommand.MarkItem(item.studentId(), item.status(), item.note()))
                .toList(), actor);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @Idempotent
    @PostMapping("/workshops/{workshopId}/attendance/check-in")
    ResponseEntity<Void> selfCheckIn(@PathVariable UUID workshopId,
                                     @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestBody SelfCheckInRequest request) {
        Actor actor = studentActor(principal);
        // Thin QR seam (Epic 3B, Slice A): the qrReference is opaque input captured here — real QR
        // resolution is Slice B (OQ-3B-1/2, backlog). The workshop candidate is the path workshopId,
        // the student comes from the authenticated principal; the handler never sees the QR itself.
        var command = new SelfCheckInCommand(workshopId, request.qrReference(), actor);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @Idempotent
    @PostMapping("/attendance-records/{recordId}/appeal")
    ResponseEntity<Void> submitAppeal(@PathVariable UUID recordId,
                                      @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestBody SubmitAppealRequest request) {
        Actor actor = studentActor(principal);
        var command = new SubmitAppealCommand(recordId, request.reason(), request.evidenceReference(), actor);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attendance-records/{recordId}/adjust")
    ResponseEntity<Void> auditorAdjust(@PathVariable UUID recordId,
                                       @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody AuditorAdjustRequest request) {
        Actor actor = auditorActor(principal);
        var command = new AuditorAdjustCommand(recordId, request.newStatus(), request.reason(),
                request.evidenceReference(), actor);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * OQ-2 mapping: a trainer decision is performed by a {@code PLANNER}/{@code ADMIN} (event
     * coordinator) until the Workshop epic adds formal instructor assignment. The ledger records the
     * actor with the contextual {@code TRAINER} role; authorization is the global-role check below.
     */
    private Actor trainerActor(AuthenticatedPrincipal principal) {
        if (!principal.hasRole("PLANNER") && !principal.hasRole("ADMIN")) {
            throw new AttendanceRoleViolationException(principal.roles().toString(), "mark attendance");
        }
        return new Actor(ActorId.of(principal.userId()), ActorRole.TRAINER);
    }

    private Actor auditorActor(AuthenticatedPrincipal principal) {
        if (!principal.hasRole("AUDITOR")) {
            throw new AttendanceRoleViolationException(principal.roles().toString(), "adjust attendance");
        }
        return new Actor(ActorId.of(principal.userId()), ActorRole.AUDITOR);
    }

    private Actor studentActor(AuthenticatedPrincipal principal) {
        return new Actor(ActorId.of(principal.userId()), ActorRole.STUDENT);
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