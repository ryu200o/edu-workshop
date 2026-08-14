package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.DuplicateAttendanceException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the JPA Attendance write adapter — full Spring context + real H2 (PostgreSQL
 * mode) + Flyway. Verifies round-trips, optimistic-lock version bumps, the unique backstop and —
 * critically — the append-only nature of the Decision Ledger (ADR 0019 §6).
 */
@SpringBootTest
class JpaAttendanceWriteAdapterTest {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceRecordJpaRepository attendanceRecordJpaRepository;

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

    private AttendanceRecord newRecord(UUID studentId) {
        return AttendanceRecord.create(
                AttendanceRecordId.generate(),
                StudentId.of(studentId),
                WORKSHOP_ID,
                AttendanceResult.PRESENT,
                null,
                TEACHER,
                NOW);
    }

    @Test
    void saveAndLoadById_roundTrip_includesLedger() {
        AttendanceRecord record = newRecord(UUID.randomUUID());
        record.markAttendance(AttendanceResult.LATE, "came at 10:15", TEACHER, NOW.plusSeconds(600));

        attendanceRecordRepository.save(record);

        AttendanceRecord loaded = attendanceRecordRepository.loadById(record.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(record.id());
        assertThat(loaded.studentId()).isEqualTo(record.studentId());
        assertThat(loaded.workshopId()).isEqualTo(record.workshopId());
        assertThat(loaded.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(loaded.currentResult()).isEqualTo(AttendanceResult.LATE);
        assertThat(loaded.reconciliationStartedAt()).isNull();
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.updatedAt()).isNotNull();
        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.entries().getFirst().entryNumber()).isEqualTo(1);
        assertThat(loaded.entries().get(1).entryNumber()).isEqualTo(2);
        assertThat(loaded.entries().get(1).action()).isEqualTo(io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction.MARK);
        assertThat(loaded.entries().get(1).reason()).isEqualTo("came at 10:15");
    }

    @Test
    void loadById_absent_returnsEmpty() {
        assertThat(attendanceRecordRepository.loadById(AttendanceRecordId.generate())).isEmpty();
    }

    @Test
    void loadByWorkshopAndStudent_findsTheSingleRow() {
        UUID studentId = UUID.randomUUID();
        AttendanceRecord record = newRecord(studentId);
        attendanceRecordRepository.save(record);

        assertThat(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, studentId))
                .isPresent()
                .get()
                .extracting(AttendanceRecord::id)
                .isEqualTo(record.id());

        assertThat(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, UUID.randomUUID())).isEmpty();
        assertThat(attendanceRecordRepository.loadByWorkshopAndStudent(UUID.randomUUID(), studentId)).isEmpty();
    }

    @Test
    void loadOpenByWorkshop_returnsOnlyOpenRecords() {
        AttendanceRecord open = newRecord(UUID.randomUUID());
        attendanceRecordRepository.save(open);

        AttendanceRecord reconciling = newRecord(UUID.randomUUID());
        reconciling.beginReconciliation(NOW, NOW);
        attendanceRecordRepository.save(reconciling);

        AttendanceRecord finalized = newRecord(UUID.randomUUID());
        finalized.beginReconciliation(NOW, NOW);
        finalized.finalizeRecord(TEACHER, NOW.plusSeconds(7200), NOW.minusSeconds(1));
        attendanceRecordRepository.save(finalized);

        List<AttendanceRecord> openRecords = attendanceRecordRepository.loadOpenByWorkshop(WORKSHOP_ID);
        assertThat(openRecords).hasSize(1);
        assertThat(openRecords.getFirst().id()).isEqualTo(open.id());
        assertThat(openRecords.getFirst().state()).isEqualTo(AttendanceState.OPEN);
    }

    @Test
    void loadNonFinalizedByWorkshop_excludesFinalizedOnly() {
        AttendanceRecord open = newRecord(UUID.randomUUID());
        attendanceRecordRepository.save(open);
        attendanceRecordRepository.save(newRecord(UUID.randomUUID()));

        AttendanceRecord finalized = newRecord(UUID.randomUUID());
        finalized.beginReconciliation(NOW, NOW);
        finalized.finalizeRecord(TEACHER, NOW.plusSeconds(7200), NOW.minusSeconds(1));
        attendanceRecordRepository.save(finalized);

        List<AttendanceRecord> nonFinalized = attendanceRecordRepository.loadNonFinalizedByWorkshop(WORKSHOP_ID);
        assertThat(nonFinalized).hasSize(2);
        assertThat(nonFinalized).allMatch(r -> r.state() != AttendanceState.FINALIZED);
    }

    @Test
    void saveAll_persistsAllRecordsInOneBatch() {
        AttendanceRecord r1 = newRecord(UUID.randomUUID());
        AttendanceRecord r2 = newRecord(UUID.randomUUID());
        AttendanceRecord r3 = newRecord(UUID.randomUUID());

        attendanceRecordRepository.saveAll(List.of(r1, r2, r3));

        assertThat(attendanceRecordRepository.loadByWorkshopId(WORKSHOP_ID)).hasSize(3);
    }

    @Test
    void duplicateInsert_isTranslatedToDuplicateAttendanceException() {
        UUID studentId = UUID.randomUUID();
        attendanceRecordRepository.save(newRecord(studentId));

        AttendanceRecord second = AttendanceRecord.create(
                AttendanceRecordId.generate(), StudentId.of(studentId), WORKSHOP_ID,
                AttendanceResult.PRESENT, null, TEACHER, NOW);

        assertThatThrownBy(() -> attendanceRecordRepository.save(second))
                .isInstanceOf(DuplicateAttendanceException.class);
    }

    @Test
    void save_thenUpdate_keepsAndIncrementsOptimisticVersion() {
        AttendanceRecord record = newRecord(UUID.randomUUID());
        attendanceRecordRepository.save(record);
        Long versionAfterInsert = attendanceRecordJpaRepository.findById(record.id().value())
                .orElseThrow().getVersion();

        record.markAttendance(AttendanceResult.ABSENT, "left early", TEACHER, NOW.plusSeconds(1800));
        attendanceRecordRepository.save(record);

        assertThat(attendanceRecordJpaRepository.findById(record.id().value())
                .orElseThrow().getVersion())
                .isEqualTo(versionAfterInsert + 1L);
    }

    @Test
    void save_thenUpdate_appendsOnlyNewLedgerEntries_insertOnly() {
        AttendanceRecord record = newRecord(UUID.randomUUID());
        attendanceRecordRepository.save(record);
        int rowsAfterInsert = rowCount(record.id().value());

        record.markAttendance(AttendanceResult.EXCUSED, "doctor's note", TEACHER, NOW.plusSeconds(1200));
        attendanceRecordRepository.save(record);

        // The ledger is append-only: the second save added exactly one new entry and never touched
        // the existing row (no DELETE/UPDATE — only an INSERT).
        int rowsAfterUpdate = rowCount(record.id().value());
        assertThat(rowsAfterUpdate).isEqualTo(rowsAfterInsert + 1);

        AttendanceRecord loaded = attendanceRecordRepository.loadById(record.id()).orElseThrow();
        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.entries()).extracting(e -> e.entryNumber()).containsExactly(1, 2);
        // the original MARK entry is untouched
        assertThat(loaded.entries().getFirst().result()).isEqualTo(AttendanceResult.PRESENT);
    }

    @Test
    void save_reconciliationFields_roundTrip() {
        AttendanceRecord record = newRecord(UUID.randomUUID());
        record.beginReconciliation(NOW, NOW);
        attendanceRecordRepository.save(record);

        AttendanceRecord loaded = attendanceRecordRepository.loadById(record.id()).orElseThrow();
        assertThat(loaded.state()).isEqualTo(AttendanceState.RECONCILING);
        assertThat(loaded.reconciliationStartedAt()).isEqualTo(NOW);
    }

    private int rowCount(UUID recordId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_entries WHERE record_id = ?",
                Integer.class, recordId);
    }
}