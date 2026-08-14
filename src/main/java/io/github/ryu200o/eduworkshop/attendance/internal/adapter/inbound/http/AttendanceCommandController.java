package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.AuditorAdjustCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.MarkAttendanceCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SelfCheckInCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SubmitAppealCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Attendance WRITE side (Command). Talks exclusively to the shared
 * {@link CommandBus}. The acting actor is derived from the authenticated principal in production;
 * in Dev/Test the {@code X-Actor-Role} + {@code X-User-Id} headers are a stand-in (ADR 0019 §8 —
 * the header is a dev convenience, never the source of truth). Error handling is centralized in
 * {@link AttendanceExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1")
class AttendanceCommandController {

    private final CommandBus commandBus;

    AttendanceCommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/workshops/{workshopId}/attendance/mark")
    ResponseEntity<MarkAttendanceCommand.Result> markAttendance(@PathVariable UUID workshopId,
                                                                @RequestHeader("X-Actor-Role") ActorRole role,
                                                                @RequestHeader("X-User-Id") UUID userId,
                                                                @RequestBody MarkAttendanceRequest request) {
        Actor actor = new Actor(ActorId.of(userId), role);
        var command = new MarkAttendanceCommand(workshopId, request.items().stream()
                .map(item -> new MarkAttendanceCommand.MarkItem(item.studentId(), item.status(), item.note()))
                .toList(), actor);
        return ResponseEntity.ok(commandBus.execute(command));
    }

    @PostMapping("/workshops/{workshopId}/attendance/check-in")
    ResponseEntity<SelfCheckInCommand.Result> selfCheckIn(@PathVariable UUID workshopId,
                                                          @RequestHeader("X-Actor-Role") ActorRole role,
                                                          @RequestHeader("X-User-Id") UUID userId,
                                                          @RequestBody SelfCheckInRequest request) {
        Actor actor = new Actor(ActorId.of(userId), role);
        // Thin QR seam (Epic 3B, Slice A): the qrReference is opaque input captured here — real QR
        // resolution is Slice B (OQ-3B-1/2, backlog). The workshop candidate is the path workshopId,
        // the student comes from the authenticated principal; the handler never sees the QR itself.
        var command = new SelfCheckInCommand(workshopId, request.qrReference(), actor);
        return ResponseEntity.ok(commandBus.execute(command));
    }

    @PostMapping("/attendance-records/{recordId}/appeal")
    ResponseEntity<SubmitAppealCommand.Result> submitAppeal(@PathVariable UUID recordId,
                                                            @RequestHeader("X-Actor-Role") ActorRole role,
                                                            @RequestHeader("X-User-Id") UUID userId,
                                                            @RequestBody SubmitAppealRequest request) {
        Actor actor = new Actor(ActorId.of(userId), role);
        var command = new SubmitAppealCommand(recordId, request.reason(), request.evidenceReference(), actor);
        return ResponseEntity.ok(commandBus.execute(command));
    }

    @PostMapping("/attendance-records/{recordId}/adjust")
    ResponseEntity<AuditorAdjustCommand.Result> auditorAdjust(@PathVariable UUID recordId,
                                                              @RequestHeader("X-Actor-Role") ActorRole role,
                                                              @RequestHeader("X-User-Id") UUID userId,
                                                              @RequestBody AuditorAdjustRequest request) {
        Actor actor = new Actor(ActorId.of(userId), role);
        var command = new AuditorAdjustCommand(recordId, request.newStatus(), request.reason(),
                request.evidenceReference(), actor);
        return ResponseEntity.ok(commandBus.execute(command));
    }

    @PostMapping("/workshops/{workshopId}/attendance/finalize")
    ResponseEntity<FinalizeWorkshopRosterCommand.Result> finalizeRoster(@PathVariable UUID workshopId,
                                                                        @RequestHeader("X-Actor-Role") ActorRole role,
                                                                        @RequestHeader("X-User-Id") UUID userId) {
        Actor actor = new Actor(ActorId.of(userId), role);
        return ResponseEntity.ok(commandBus.execute(new FinalizeWorkshopRosterCommand(workshopId, actor)));
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