package io.github.ryu200o.eduworkshop.attendance.internal.application.event;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopCompletedIntegrationEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AttendanceWorkshopCompletedEventHandler} — full Spring context + real
 * H2 + Flyway. Seeds attendance records, then invokes the listener directly (it runs in its own
 * {@code REQUIRES_NEW} transaction) and asserts every non-finalized record moved
 * {@code OPEN → RECONCILING} anchored to the authoritative {@code completedAt} (ADR 0019 §4), and
 * that replay is idempotent (outbox replay-safe).
 */
@SpringBootTest
class AttendanceWorkshopCompletedEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-01T12:00:00Z");
    private static final Actor SYSTEM = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.SYSTEM);
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceWorkshopCompletedEventHandler handler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM attendance_entries");
        jdbcTemplate.update("DELETE FROM attendance_records");
    }

    private void save(AttendanceRecord record) {
        transactionTemplate.executeWithoutResult(status -> attendanceRecordRepository.save(record));
    }

    private AttendanceRecord openRecord(UUID id, UUID studentId) {
        return AttendanceRecord.create(AttendanceRecordId.of(id), StudentId.of(studentId),
                WORKSHOP_ID, AttendanceResult.PRESENT, null,
                new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER), NOW);
    }

    @Test
    void handle_opensReconciliationWindowOnEveryNonFinalizedRecord() {
        save(openRecord(UUID.randomUUID(), UUID.randomUUID()));
        save(openRecord(UUID.randomUUID(), UUID.randomUUID()));

        handler.handle(new WorkshopCompletedIntegrationEvent(WORKSHOP_ID, COMPLETED_AT));

        List<AttendanceRecord> records = attendanceRecordRepository.loadByWorkshopId(WORKSHOP_ID);
        assertThat(records)
                .allMatch(r -> r.state() == AttendanceState.RECONCILING)
                .allMatch(r -> r.reconciliationStartedAt().equals(COMPLETED_AT))
                .hasSize(2);
        // The query was narrow (OPEN only) — nothing remains OPEN for a replay to re-select.
        assertThat(attendanceRecordRepository.loadOpenByWorkshop(WORKSHOP_ID)).isEmpty();
    }

    @Test
    void handle_isIdempotentOnReplayedEvent() {
        AttendanceRecord record = openRecord(UUID.randomUUID(), UUID.randomUUID());
        save(record);

        handler.handle(new WorkshopCompletedIntegrationEvent(WORKSHOP_ID, COMPLETED_AT));
        handler.handle(new WorkshopCompletedIntegrationEvent(WORKSHOP_ID, COMPLETED_AT));

        List<AttendanceRecord> records = attendanceRecordRepository.loadByWorkshopId(WORKSHOP_ID);
        assertThat(records).allMatch(r -> r.state() == AttendanceState.RECONCILING);
        assertThat(records).allMatch(r -> r.reconciliationStartedAt().equals(COMPLETED_AT));
    }

    @Test
    void handle_doesNothingWhenWorkshopHasNoRecords() {
        handler.handle(new WorkshopCompletedIntegrationEvent(WORKSHOP_ID, COMPLETED_AT));

        assertThat(attendanceRecordRepository.loadByWorkshopId(WORKSHOP_ID)).isEmpty();
    }
}