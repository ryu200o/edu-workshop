package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.AuditorAdjustCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the auditor-adjustment use case during the Reconciliation Window — the only
 * authoritative mutation of {@code currentResult} in that window (ADR 0019 §5).
 *
 * <p>Application-layer flow (ADR 0005): load the record (404 when missing) → authorize the actor as
 * {@code AUDITOR} → delegate {@code auditorAdjust} (reason + evidenceReference both mandatory → 400)
 * → persist and publish.</p>
 */
@Component
class AuditorAdjustCommandHandler implements CommandHandler<AuditorAdjustCommand> {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final Clock clock;

    AuditorAdjustCommandHandler(AttendanceRecordRepository attendanceRecordRepository,
                                AttendanceDomainEventPublisher attendanceDomainEventPublisher,
                                Clock clock) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceDomainEventPublisher = attendanceDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(AuditorAdjustCommand command) {
        Instant now = Instant.now(clock);
        AttendanceRecordId recordId = AttendanceRecordId.of(command.recordId());
        Actor actor = new Actor(new ActorId(command.actorId()), ActorRole.AUDITOR);

        AttendanceRecord record = attendanceRecordRepository.loadById(recordId)
                .orElseThrow(() -> new AttendanceNotFoundException(command.recordId()));

        record.auditorAdjust(command.newStatus(), command.reason(), command.evidenceReference(),
                actor, now);

        List<AttendanceDomainEvent> events = List.copyOf(record.recordedEvents());
        record.clearDomainEvents();
        attendanceRecordRepository.save(record);
        attendanceDomainEventPublisher.publish(events);
    }
}