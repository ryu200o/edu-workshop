package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotCompletedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.MissingReconciliationAnchorException;
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
        implements CommandHandler<FinalizeWorkshopRosterCommand> {

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
    public void handle(FinalizeWorkshopRosterCommand command) {
        Instant now = Instant.now(clock);
        UUID workshopId = command.workshopId();
        ActorRole role = command.actorId().equals(FinalizeWorkshopRosterCommand.SYSTEM_ACTOR_ID)
                ? ActorRole.SYSTEM : ActorRole.AUDITOR;
        Actor actor = new Actor(new ActorId(command.actorId()), role);

        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(workshopId)
                .orElseThrow(() -> new ReferencedWorkshopNotFoundException(workshopId));

        // Fail fast: this system-initiated flow must only run once the workshop is COMPLETED. The
        // Reconciliation Window is anchored to WorkshopCompleted.completedAt (ADR 0019 §4), so a
        // non-COMPLETED workshop is a scheduling/ordering error — an explicit guard avoids an OPEN
        // record blowing up inside the domain with a confusing state error.
        if (workshop.state() != WorkshopStateContract.COMPLETED) {
            throw new WorkshopNotCompletedException(workshopId, workshop.state().name());
        }
        Instant completedAt = workshop.completedAt();

        List<AttendanceRecord> records = attendanceRecordRepository.loadNonFinalizedByWorkshop(workshopId);

        List<AttendanceDomainEvent> allEvents = new ArrayList<>();
        for (AttendanceRecord record : records) {
            // Recovery: an OPEN record whose workshop is COMPLETED was never reconciled (event lost).
            // Anchor the window to the authoritative completedAt — never `now` (OQ-4 / OQ-10).
            if (record.state() == AttendanceState.OPEN) {
                record.beginReconciliation(completedAt, now);
            }

            // Invariant (ADR 0019 §4): a RECONCILING record must carry the anchor, since
            // beginReconciliation always snapshots completedAt. A null anchor is corrupted state —
            // fail fast with a clear exception instead of silently passing a null deadline.
            Instant startedAt = record.reconciliationStartedAt();
            if (record.state() == AttendanceState.RECONCILING && startedAt == null) {
                throw new MissingReconciliationAnchorException(record.id());
            }
            Instant deadline = startedAt.plus(Duration.ofMinutes(reconciliationParameters.windowMinutes()));
            record.finalizeRecord(actor, now, deadline);

            allEvents.addAll(record.recordedEvents());
            record.clearDomainEvents();
        }

        attendanceRecordRepository.saveAll(records);
        attendanceDomainEventPublisher.publish(allEvents);
    }
}