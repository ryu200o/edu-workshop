package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.DuplicateAttendanceException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceEntry;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed outbound adapter implementing the Attendance write port ({@link AttendanceRecordRepository}).
 * Domain ↔ entity mapping is performed entirely here, keeping the domain framework-free.
 *
 * <p><strong>Append-only ledger (ADR 0019 §6):</strong> the master is upserted via a managed-entity
 * copy, and brand-new ledger rows are inserted <em>explicitly</em> through
 * {@link AttendanceEntryJpaRepository}. The {@code @OneToMany} collection is read-only (no cascade):
 * existing rows are never merged, updated or deleted — the composite key
 * {@code (record_id, entry_number)} is the schema gate and the mapping keeps it honest.</p>
 *
 * <p>The unique index {@code uq_student_workshop} is the race-proof backstop for the
 * one-record-per-(workshop, student) rule: a {@link DataIntegrityViolationException} from a
 * concurrent insert is translated into {@link DuplicateAttendanceException}.</p>
 *
 * <p>Concurrency (ADR 0015): single-aggregate mutations rely on optimistic locking — the
 * {@code @Version} column on the master; {@code saveAndFlush} (Golden Rule 1) surfaces both the
 * version conflict and the unique-violation inside this try-catch. {@code saveAll} uses plain
 * {@code save} (Golden Rule 2) — only existing rows are flipped by event handlers.</p>
 */
@Component
class JpaAttendanceWriteAdapter implements AttendanceRecordRepository {

    private final AttendanceRecordJpaRepository repository;
    private final AttendanceEntryJpaRepository entryRepository;

    JpaAttendanceWriteAdapter(AttendanceRecordJpaRepository repository,
                              AttendanceEntryJpaRepository entryRepository) {
        this.repository = repository;
        this.entryRepository = entryRepository;
    }

    @Override
    public AttendanceRecord save(AttendanceRecord record) {
        try {
            AttendanceRecordJpaEntity entity = repository.findById(record.id().value())
                    .map(existing -> copyTo(existing, record))
                    .orElseGet(() -> toEntity(record));
            repository.saveAndFlush(entity);
            insertNewEntries(entity, record);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAttendanceException(record.workshopId(), record.studentId().value());
        }
        return record;
    }

    @Override
    public void saveAll(List<AttendanceRecord> records) {
        // Plain save (Golden Rule 2): flush deferred to commit; used only by event handlers opening
        // the Reconciliation Window on EXISTING rows (no unique-violation scenario). No new entries
        // are produced by beginReconciliation, so the ledger stays untouched.
        for (AttendanceRecord record : records) {
            AttendanceRecordJpaEntity entity = repository.findById(record.id().value())
                    .map(existing -> copyTo(existing, record))
                    .orElseGet(() -> toEntity(record));
            repository.save(entity);
            insertNewEntries(entity, record);
        }
    }

    @Override
    public Optional<AttendanceRecord> loadById(AttendanceRecordId id) {
        return repository.findById(id.value()).map(JpaAttendanceWriteAdapter::toAttendanceRecord);
    }

    @Override
    public Optional<AttendanceRecord> loadByWorkshopAndStudent(UUID workshopId, UUID studentId) {
        return repository.findByWorkshopIdAndStudentId(workshopId, studentId).stream()
                .findFirst()
                .map(JpaAttendanceWriteAdapter::toAttendanceRecord);
    }

    @Override
    public List<AttendanceRecord> loadNonFinalizedByWorkshop(UUID workshopId) {
        return repository.findByWorkshopIdAndStateNot(workshopId, AttendanceState.FINALIZED.name()).stream()
                .map(JpaAttendanceWriteAdapter::toAttendanceRecord)
                .toList();
    }

    @Override
    public List<AttendanceRecord> loadByWorkshopId(UUID workshopId) {
        return repository.findByWorkshopId(workshopId).stream()
                .map(JpaAttendanceWriteAdapter::toAttendanceRecord)
                .toList();
    }

    // ====================== MAPPER ======================

    private static AttendanceRecordJpaEntity toEntity(AttendanceRecord record) {
        AttendanceRecordJpaEntity entity = new AttendanceRecordJpaEntity();
        entity.setId(record.id().value());
        return copyTo(entity, record);
    }

    /**
     * Copies the mutable business fields onto an existing (managed) entity, leaving {@code id} and
     * {@code version} untouched so Hibernate increments/checks the optimistic-lock version on flush.
     * Ledger rows are deliberately NOT touched here — they are handled by {@link #insertNewEntries}.
     */
    private static AttendanceRecordJpaEntity copyTo(AttendanceRecordJpaEntity entity, AttendanceRecord record) {
        entity.setStudentId(record.studentId().value());
        entity.setWorkshopId(record.workshopId());
        entity.setCurrentResult(record.currentResult().name());
        entity.setState(record.state().name());
        entity.setReconciliationStartedAt(record.reconciliationStartedAt());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    /**
     * Inserts only the ledger rows that are not yet persisted — an entry with {@code entryNumber}
     * strictly greater than the highest existing {@code entry_number}. Existing rows are never
     * touched. Never deletes.
     */
    private void insertNewEntries(AttendanceRecordJpaEntity entity, AttendanceRecord record) {
        int existingHighest = entity.getEntries().stream()
                .mapToInt(e -> e.getId().getEntryNumber())
                .max()
                .orElse(0);
        List<AttendanceEntryJpaEntity> newEntries = record.entries().stream()
                .filter(entry -> entry.entryNumber() > existingHighest)
                .map(entry -> toEntryEntity(entity, entry))
                .toList();
        if (!newEntries.isEmpty()) {
            entryRepository.saveAll(newEntries);
        }
    }

    private static AttendanceEntryJpaEntity toEntryEntity(AttendanceRecordJpaEntity record, AttendanceEntry entry) {
        AttendanceEntryJpaEntity entity = new AttendanceEntryJpaEntity();
        entity.setId(new AttendanceEntryId(record.getId(), entry.entryNumber()));
        entity.setRecord(record);
        entity.setTimestamp(entry.timestamp());
        entity.setActorId(entry.actor().id().value());
        entity.setActorRole(entry.actor().role().name());
        entity.setAction(entry.action().name());
        entity.setResult(entry.result().name());
        entity.setReason(entry.reason());
        entity.setEvidenceReference(entry.evidenceReference());
        entity.setCreatedAt(entry.timestamp());
        return entity;
    }

    private static AttendanceRecord toAttendanceRecord(AttendanceRecordJpaEntity entity) {
        List<AttendanceEntry> entries = entity.getEntries().stream()
                .map(JpaAttendanceWriteAdapter::toEntry)
                .toList();
        return AttendanceRecord.reconstruct(
                AttendanceRecordId.of(entity.getId()),
                StudentId.of(entity.getStudentId()),
                entity.getWorkshopId(),
                entity.getReconciliationStartedAt(),
                AttendanceResult.valueOf(entity.getCurrentResult()),
                AttendanceState.valueOf(entity.getState()),
                entries,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static AttendanceEntry toEntry(AttendanceEntryJpaEntity entity) {
        return new AttendanceEntry(
                entity.getId().getEntryNumber(),
                entity.getTimestamp(),
                new Actor(ActorId.of(entity.getActorId()), ActorRole.valueOf(entity.getActorRole())),
                AttendanceAction.valueOf(entity.getAction()),
                AttendanceResult.valueOf(entity.getResult()),
                entity.getReason(),
                entity.getEvidenceReference());
    }
}