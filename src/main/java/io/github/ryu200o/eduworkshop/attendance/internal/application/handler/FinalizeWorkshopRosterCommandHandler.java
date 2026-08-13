package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Finalizes every non-finalized attendance record of a workshop once the Reconciliation Window has
 * closed ({@code RECONCILING → FINALIZED}, permanently locked). System-initiated.
 *
 * <p><strong>Recovery path (ADR 0019 §4 / OQ-10):</strong> a record left {@code OPEN} because the
 * {@code WorkshopCompletedIntegrationEvent} was lost is reconciled first — anchored to the
 * <em>authoritative</em> {@code WorkshopSchedulingContract.completedAt} from
 * {@link WorkshopExposeAPI#getScheduling}, never to {@code now}. Then each record is finalized
 * against {@code reconciliationStartedAt + windowMinutes}.</p>
 */
@Component
class FinalizeWorkshopRosterCommandHandler
        implements CommandHandler<FinalizeWorkshopRosterCommand, FinalizeWorkshopRosterCommand.Result> {

    private final WorkshopExposeAPI workshopExposeApi;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final AttendanceReconciliationParameters reconciliationParameters;
    private final Clock clock;

    FinalizeWorkshopRosterCommandHandler(WorkshopExposeAPI workshopExposeApi,
                                         AttendanceRecordRepository attendanceRecordRepository,
                                         AttendanceDomainEventPublisher attendanceDomainEventPublisher,
                                         AttendanceReconciliationParameters reconciliationParameters,
                                         Clock clock) {
        this.workshopExposeApi = workshopExposeApi;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceDomainEventPublisher = attendanceDomainEventPublisher;
        this.reconciliationParameters = reconciliationParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FinalizeWorkshopRosterCommand.Result handle(FinalizeWorkshopRosterCommand command) {
        Instant now = Instant.now(clock);
        UUID workshopId = command.workshopId();

        if (command.actor().role() != ActorRole.SYSTEM) {
            throw new AttendanceRoleViolationException(command.actor().role().name(), "finalize the workshop roster");
        }

        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(workshopId)
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(workshopId));
        Instant completedAt = workshop.state() == WorkshopStateContract.COMPLETED ? workshop.completedAt() : null;

        List<AttendanceRecord> records = attendanceRecordRepository.loadNonFinalizedByWorkshop(workshopId);

        List<AttendanceDomainEvent> allEvents = new ArrayList<>();
        int finalized = 0;
        for (AttendanceRecord record : records) {
            // Recovery: an OPEN record whose workshop is COMPLETED was never reconciled (event lost).
            // Anchor the window to the authoritative completedAt — never `now` (OQ-4 / OQ-10).
            if (record.state() == AttendanceState.OPEN && completedAt != null) {
                record.beginReconciliation(completedAt, now);
            }

            Instant startedAt = record.reconciliationStartedAt();
            Instant deadline = startedAt != null
                    ? startedAt.plus(Duration.ofMinutes(reconciliationParameters.windowMinutes()))
                    : null;
            record.finalizeRecord(command.actor(), now, deadline);
            finalized++;

            allEvents.addAll(record.recordedEvents());
            record.clearDomainEvents();
        }

        attendanceRecordRepository.saveAll(records);
        attendanceDomainEventPublisher.publish(allEvents);

        return new FinalizeWorkshopRosterCommand.Result(workshopId, finalized, AttendanceState.FINALIZED.name(), now);
    }
}