package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.RegistrationNotVerifiedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotInSessionException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.mapper.AttendanceMapper;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SelfCheckInCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the "QR self check-in" use case (Epic 3B) — a learner checks in by scanning a QR
 * while the workshop is {@code IN_PROGRESS}, adding a {@code MARK} entry with actor role
 * {@code STUDENT} to their append-only Decision Ledger.
 *
 * <p>Application-layer flow (ADR 0005 — global rules orchestrated here, aggregate stays pure;
 * <strong>thin QR</strong> — the {@code qrReference} is opaque and never interpreted here,
 * Slice B owns real resolution):</p>
 * <ol>
 *   <li>resolve the workshop via {@link WorkshopExposeAPI#getScheduling} → exists &amp;
 *       {@code IN_PROGRESS} (OQ-3B-4; {@code Workshop.state} is the lifecycle authority, never
 *       inferred from time);</li>
 *   <li>authorize the actor as {@code STUDENT};</li>
 *   <li><strong>VERIFIED gate (SA directive)</strong>: the learner's registration must be
 *       {@code VERIFIED} via {@link RegistrationExposeAPI#isVerified} — fail-fast;</li>
 *   <li><strong>idempotent no-op (OQ-3B-3)</strong>: if an {@code OPEN} record already exists for
 *       (workshop, student) — from an earlier scan or a trainer mark — return its current
 *       {@code recordId}/{@code result}/{@code state} unchanged: no new ledger entry, no publish,
 *       no {@code currentResult} change (a student must never self-correct, ADR 0019 §5);</li>
 *   <li>otherwise let the Workshop module evaluate the check-in ({@code ATTENDED | LATE},
 *       OQ-3B-5), create the record with {@code create(...)} (the entry is exactly a trainer's
 *       {@code MARK}, just with role {@code STUDENT}), persist and publish through the outbox
 *       (ADR 0011).</li>
 * </ol>
 */
@Component
class SelfCheckInCommandHandler implements CommandHandler<SelfCheckInCommand, SelfCheckInCommand.Result> {

    private final WorkshopExposeAPI workshopExposeApi;
    private final RegistrationExposeAPI registrationExposeApi;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final Clock clock;

    SelfCheckInCommandHandler(WorkshopExposeAPI workshopExposeApi,
                              RegistrationExposeAPI registrationExposeApi,
                              AttendanceRecordRepository attendanceRecordRepository,
                              AttendanceDomainEventPublisher attendanceDomainEventPublisher,
                              Clock clock) {
        this.workshopExposeApi = workshopExposeApi;
        this.registrationExposeApi = registrationExposeApi;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceDomainEventPublisher = attendanceDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SelfCheckInCommand.Result handle(SelfCheckInCommand command) {
        Instant now = Instant.now(clock);
        UUID workshopId = command.workshopId();
        UUID studentId = command.actor().id().value();

        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(workshopId)
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(workshopId));
        if (workshop.state() != WorkshopStateContract.IN_PROGRESS) {
            throw new WorkshopNotInSessionException(workshopId, workshop.state().name());
        }
        if (command.actor().role() != ActorRole.STUDENT) {
            throw new AttendanceRoleViolationException(command.actor().role().name(), "QR self check-in");
        }
        if (!registrationExposeApi.isVerified(workshopId, studentId)) {
            throw new RegistrationNotVerifiedException(workshopId, List.of(studentId));
        }

        return attendanceRecordRepository.loadByWorkshopAndStudent(workshopId, studentId)
                .<SelfCheckInCommand.Result>map(existing -> new SelfCheckInCommand.Result(
                        existing.id().value(),
                        existing.currentResult(),
                        existing.state()))
                .orElseGet(() -> {
                    AttendanceStatusContract status = workshopExposeApi.evaluateCheckIn(workshopId, now)
                            .orElseThrow(() -> new ReferencedWorkshopNotFoundException(workshopId));
                    AttendanceRecord record = AttendanceRecord.create(
                            AttendanceRecordId.generate(),
                            StudentId.of(studentId),
                            workshopId,
                            AttendanceMapper.toResult(status),
                            null,
                            command.actor(),
                            now);
                    attendanceRecordRepository.save(record);
                    attendanceDomainEventPublisher.publish(record.recordedEvents());
                    record.clearDomainEvents();
                    return new SelfCheckInCommand.Result(record.id().value(), record.currentResult(), record.state());
                });
    }
}
