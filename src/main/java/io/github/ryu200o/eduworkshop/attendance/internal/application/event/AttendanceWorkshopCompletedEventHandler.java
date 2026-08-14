package io.github.ryu200o.eduworkshop.attendance.internal.application.event;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopCompletedIntegrationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link WorkshopCompletedIntegrationEvent} (delivered via the transactional outbox) and
 * opens the Reconciliation Window on every {@code OPEN} attendance record of the completed workshop
 * ({@code OPEN → RECONCILING}), anchoring {@code reconciliationStartedAt} to the event's
 * authoritative {@code completedAt} (ADR 0019 §4).
 *
 * <p><strong>Idempotent (outbox replay-safe):</strong> only {@code OPEN} records are loaded
 * ({@link AttendanceRecordRepository#loadOpenByWorkshop}); after the first delivery every record is
 * {@code RECONCILING}, so a re-delivered event loads nothing — the replay is a natural no-op. The
 * aggregate's {@code beginReconciliation} (no-op on non-OPEN) and the optimistic lock remain the
 * backstop for any race between load and mutation.</p>
 *
 * <p>Cross-module collaboration per ADR 0010 / ADR 0011: Attendance reacts to the Workshop module's
 * integration event — never a direct call. Runs {@code AFTER_COMMIT} in a new transaction
 * ({@code REQUIRES_NEW}) so a failure here never rolls back the business transaction; the outbox
 * guarantees durable (re)delivery.</p>
 */
@Component
public class AttendanceWorkshopCompletedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceWorkshopCompletedEventHandler.class);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDomainEventPublisher attendanceDomainEventPublisher;
    private final Clock clock;

    AttendanceWorkshopCompletedEventHandler(AttendanceRecordRepository attendanceRecordRepository,
                                            AttendanceDomainEventPublisher attendanceDomainEventPublisher,
                                            Clock clock) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceDomainEventPublisher = attendanceDomainEventPublisher;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkshopCompletedIntegrationEvent event) {
        Instant now = Instant.now(clock);
        List<AttendanceRecord> records = attendanceRecordRepository.loadOpenByWorkshop(event.workshopId());

        if (records.isEmpty()) {
            return;
        }

        log.info("Workshop {} completed at {} — opening reconciliation on {} OPEN record(s)",
                event.workshopId(), event.completedAt(), records.size());

        // 1. Domain State Mutation & Event Collection (beginReconciliation records no event — the
        //    window opening itself is anchored on the WorkshopCompleted business fact).
        List<AttendanceDomainEvent> allDomainEvents = new ArrayList<>();
        for (AttendanceRecord record : records) {
            record.beginReconciliation(event.completedAt(), now);
            allDomainEvents.addAll(record.recordedEvents());
            record.clearDomainEvents();
        }

        // 2. Batch Persistence (plain save per record, one TX — OQ-11)
        attendanceRecordRepository.saveAll(records);

        // 3. Batch Event Publication
        attendanceDomainEventPublisher.publish(allDomainEvents);
    }
}