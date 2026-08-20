package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.RegistrationNotVerifiedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotInSessionException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.MarkAttendanceCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the "mark attendance" use case — a trainer records (or corrects) the attendance of a
 * batch of learners while the workshop is {@code IN_PROGRESS}.
 *
 * <p>Application-layer flow (ADR 0005 — global rules orchestrated here, aggregate stays pure):
 * (1) resolve the workshop via {@link WorkshopExposeAPI#getScheduling} → exists &amp; {@code IN_PROGRESS}
 * (OQ-5; {@code Workshop.state} is the lifecycle authority, never inferred from time);
 * (2) authorize the actor as {@code TRAINER};
 * (3) <strong>VERIFIED gate (SA directive, OQ-14)</strong>: check
 * {@code registrationExposeApi.isVerified(workshopId, studentId)} for ALL students <em>before</em>
 * touching any record — if any is not verified, throw {@link RegistrationNotVerifiedException} and
 * process nothing (fail-fast, atomic over the whole batch);
 * (4) load-or-create each (workshop, student) record and call {@code create}/{@code markAttendance};
 * (5) persist and publish domain events through the outbox (ADR 0011).</p>
 */
@Component
class MarkAttendanceCommandHandler implements CommandHandler<MarkAttendanceCommand> {

    private final WorkshopExposeAPI workshopExposeApi;
    private final RegistrationExposeAPI registrationExposeApi;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final Clock clock;

    MarkAttendanceCommandHandler(WorkshopExposeAPI workshopExposeApi,
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
    public void handle(MarkAttendanceCommand command) {
        Instant now = Instant.now(clock);
        UUID workshopId = command.workshopId();

        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(workshopId)
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(workshopId));
        if (workshop.state() != WorkshopStateContract.IN_PROGRESS) {
            throw new WorkshopNotInSessionException(workshopId, workshop.state().name());
        }
        if (command.actor().role() != ActorRole.TRAINER) {
            throw new AttendanceRoleViolationException(command.actor().role().name(), "mark attendance");
        }

        List<UUID> unverified = command.items().stream()
                .map(MarkAttendanceCommand.MarkItem::studentId)
                .filter(studentId -> !registrationExposeApi.isVerified(workshopId, studentId))
                .toList();
        if (!unverified.isEmpty()) {
            throw new RegistrationNotVerifiedException(workshopId, unverified);
        }

        List<AttendanceDomainEvent> allEvents = new ArrayList<>();
        for (MarkAttendanceCommand.MarkItem item : command.items()) {
            AttendanceRecord record = attendanceRecordRepository
                    .loadByWorkshopAndStudent(workshopId, item.studentId())
                    .map(existing -> {
                        existing.markAttendance(item.status(), item.note(), command.actor(), now);
                        return existing;
                    })
                    .orElseGet(() -> AttendanceRecord.create(
                            AttendanceRecordId.generate(),
                            StudentId.of(item.studentId()),
                            workshopId,
                            item.status(),
                            item.note(),
                            command.actor(),
                            now));
            allEvents.addAll(record.recordedEvents());
            record.clearDomainEvents();
            attendanceRecordRepository.save(record);
        }

        attendanceDomainEventPublisher.publish(allEvents);
    }
}