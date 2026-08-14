package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SubmitAppealCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.MissingReconciliationAnchorException;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the student-appeal use case during the Reconciliation Window.
 *
 * <p>Application-layer flow (ADR 0005): load the record (404 when missing) → authorize the actor as
 * {@code STUDENT} → compute the reconciliation deadline from the operational window
 * ({@code reconciliationStartedAt + windowMinutes}, ADR 0019 §4) → delegate
 * {@code submitAppeal} (which rejects a closed window with
 * {@code ReconciliationWindowExceededException} → 409) → persist and publish.</p>
 */
@Component
class SubmitAppealCommandHandler implements CommandHandler<SubmitAppealCommand, SubmitAppealCommand.Result> {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final AttendanceReconciliationParameters reconciliationParameters;
    private final Clock clock;

    SubmitAppealCommandHandler(AttendanceRecordRepository attendanceRecordRepository,
                               AttendanceDomainEventPublisher attendanceDomainEventPublisher,
                               AttendanceReconciliationParameters reconciliationParameters,
                               Clock clock) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceDomainEventPublisher = attendanceDomainEventPublisher;
        this.reconciliationParameters = reconciliationParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubmitAppealCommand.Result handle(SubmitAppealCommand command) {
        Instant now = Instant.now(clock);
        AttendanceRecordId recordId = AttendanceRecordId.of(command.recordId());

        if (command.actor().role() != ActorRole.STUDENT) {
            throw new AttendanceRoleViolationException(command.actor().role().name(), "submit an appeal");
        }

        AttendanceRecord record = attendanceRecordRepository.loadById(recordId)
                .orElseThrow(() -> new AttendanceNotFoundException(command.recordId()));

        // The deadline is only meaningful once the window is open (reconciliationStartedAt set).
        // A RECONCILING record must carry the anchor — beginReconciliation always snapshots
        // WorkshopCompleted.completedAt (ADR 0019 §4), so a null anchor is corrupted state and we
        // fail fast with a clear invariant exception instead of passing a null deadline downstream.
        // (An OPEN record has no anchor yet; the domain raises AttendanceStateException for it.)
        AttendanceRecordId id = record.id();
        AttendanceState state = record.state();
        Instant startedAt = record.reconciliationStartedAt();
        if (state == AttendanceState.RECONCILING && startedAt == null) {
            throw new MissingReconciliationAnchorException(id);
        }
        Instant deadline = startedAt != null
                ? startedAt.plus(Duration.ofMinutes(reconciliationParameters.windowMinutes()))
                : null;
        record.submitAppeal(command.reason(), command.evidenceReference(), command.actor(), deadline, now);

        List<AttendanceDomainEvent> events = List.copyOf(record.recordedEvents());
        record.clearDomainEvents();
        attendanceRecordRepository.save(record);
        attendanceDomainEventPublisher.publish(events);

        int entryNumber = record.entries().size();
        return new SubmitAppealCommand.Result(
                record.id().value(),
                entryNumber,
                record.state().name(),
                "Appeal submitted — current result unchanged; pending auditor review");
    }
}