package io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Write-side outbound port (SPI) for the {@code AttendanceRecord} aggregate. Per ADR 0016 it uses
 * {@code load*} naming (aggregate reconstruction); the read side lives in {@link AttendanceRecordReader}.
 * Implementations must persist the append-only Decision Ledger insert-only (entries are never
 * updated or deleted).
 */
public interface AttendanceRecordRepository {

    /**
     * Persists a single aggregate (managed-entity copy + {@code saveAndFlush}). A
     * {@code DataIntegrityViolationException} from the {@code uq_student_workshop} backstop is
     * translated by the adapter into the business exception.
     */
    AttendanceRecord save(AttendanceRecord record);

    /**
     * Persists a batch of aggregates within the caller's single transaction. Uses a plain per-record
     * {@code save} (dirty-check, deferred flush) — correctness over batch optimization (OQ-11).
     */
    void saveAll(List<AttendanceRecord> records);

    Optional<AttendanceRecord> loadById(AttendanceRecordId id);

    Optional<AttendanceRecord> loadByWorkshopAndStudent(UUID workshopId, UUID studentId);

    /**
     * Loads every non-finalized record of a workshop — the completion-event handler needs exactly
     * these to open the Reconciliation Window (ADR 0019 §4).
     */
    List<AttendanceRecord> loadNonFinalizedByWorkshop(UUID workshopId);

    List<AttendanceRecord> loadByWorkshopId(UUID workshopId);
}