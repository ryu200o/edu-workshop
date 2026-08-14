package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterEntryView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ Attendance read adapter — full Spring context + real H2
 * (PostgreSQL mode) + Flyway. Proves the read path builds the ledger and the roster summary
 * directly from flat SQL (no JPA entity, no domain reconstruction — CQRS bypass). Rows are seeded
 * via {@link AttendanceRecordRepository} (JPA) since this adapter is read-only by design.
 */
@SpringBootTest
class JooqAttendanceReadAdapterTest {

    @Autowired
    private AttendanceRecordReader attendanceRecordReader;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Actor TEACHER = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);
    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM attendance_entries");
        jdbcTemplate.update("DELETE FROM attendance_records");
    }

    private AttendanceRecord newRecord(UUID studentId, AttendanceResult result) {
        return AttendanceRecord.create(
                AttendanceRecordId.generate(),
                StudentId.of(studentId),
                WORKSHOP_ID,
                result,
                null,
                TEACHER,
                NOW);
    }

    @Test
    void getById_absent_returnsEmpty() {
        assertThat(attendanceRecordReader.getById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getById_returnsMasterWithLedgerInOrder() {
        AttendanceRecord record = newRecord(UUID.randomUUID(), AttendanceResult.PRESENT);
        record.markAttendance(AttendanceResult.LATE, "late entry", TEACHER, NOW.plusSeconds(300));
        attendanceRecordRepository.save(record);

        AttendanceRecordLedgerView view = attendanceRecordReader.getById(record.id().value()).orElseThrow();

        assertThat(view.recordId()).isEqualTo(record.id().value());
        assertThat(view.studentId()).isEqualTo(record.studentId().value());
        assertThat(view.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(view.currentResult()).isEqualTo(AttendanceResult.LATE);
        assertThat(view.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries()).extracting(e -> e.entryNumber()).containsExactly(1, 2);
        assertThat(view.entries().getFirst().action().name()).isEqualTo("MARK");
        assertThat(view.entries().getFirst().result()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(view.entries().get(1).result()).isEqualTo(AttendanceResult.LATE);
        assertThat(view.entries().get(1).reason()).isEqualTo("late entry");
    }

    @Test
    void getByWorkshopId_noFilter_buildsRosterAndSummary() {
        AttendanceRecord present = newRecord(UUID.randomUUID(), AttendanceResult.PRESENT);
        attendanceRecordRepository.save(present);
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.PRESENT));
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.ABSENT));

        AttendanceRecord reconciled = newRecord(UUID.randomUUID(), AttendanceResult.LATE);
        reconciled.beginReconciliation(NOW, NOW);
        attendanceRecordRepository.save(reconciled);

        AttendanceRosterView roster = attendanceRecordReader.getByWorkshopId(WORKSHOP_ID, null, null);

        assertThat(roster.total()).isEqualTo(4);
        assertThat(roster.present()).isEqualTo(2);
        assertThat(roster.late()).isEqualTo(1);
        assertThat(roster.absent()).isEqualTo(1);
        assertThat(roster.excused()).isZero();
        assertThat(roster.records()).hasSize(4);
        assertThat(roster.records()).extracting(AttendanceRosterEntryView::recordId)
                .contains(present.id().value());
    }

    @Test
    void getByWorkshopId_withResultFilter_returnsOnlyMatchingRows() {
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.PRESENT));
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.PRESENT));
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.ABSENT));

        AttendanceRosterView filtered = attendanceRecordReader.getByWorkshopId(WORKSHOP_ID, AttendanceResult.PRESENT, null);

        assertThat(filtered.total()).isEqualTo(2);
        assertThat(filtered.records()).hasSize(2);
        assertThat(filtered.records()).allMatch(r -> r.currentResult() == AttendanceResult.PRESENT);
        assertThat(filtered.present()).isEqualTo(2);
        assertThat(filtered.late()).isZero();
    }

    @Test
    void getByWorkshopId_withStateFilter_returnsOnlyMatchingRows() {
        attendanceRecordRepository.save(newRecord(UUID.randomUUID(), AttendanceResult.PRESENT));
        AttendanceRecord reconciled = newRecord(UUID.randomUUID(), AttendanceResult.LATE);
        reconciled.beginReconciliation(NOW, NOW);
        attendanceRecordRepository.save(reconciled);

        AttendanceRosterView filtered = attendanceRecordReader.getByWorkshopId(WORKSHOP_ID, null, AttendanceState.RECONCILING);

        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.records()).hasSize(1);
        assertThat(filtered.records().getFirst().state()).isEqualTo(AttendanceState.RECONCILING);
    }

    @Test
    void getByWorkshopId_noRecords_returnsEmptyRoster() {
        AttendanceRosterView roster = attendanceRecordReader.getByWorkshopId(WORKSHOP_ID, null, null);

        assertThat(roster.total()).isZero();
        assertThat(roster.present()).isZero();
        assertThat(roster.records()).isEmpty();
    }
}