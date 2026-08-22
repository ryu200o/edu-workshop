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
     * Loads only the {@code OPEN} records of a workshop — exactly the set the completion-event
     * handler needs to open the Reconciliation Window ({@code OPEN → RECONCILING}, ADR 0019 §4).
     * Narrower than {@link #loadNonFinalizedByWorkshop}: records already {@code RECONCILING} would
     * no-op in {@code beginReconciliation}, so this query matches the domain intent. Replay-safe:
     * after the first delivery every record is {@code RECONCILING}, so a re-delivered event loads
     * nothing.
     */
    List<AttendanceRecord> loadOpenByWorkshop(UUID workshopId);

    /**
     * Loads every non-finalized ({@code OPEN} or {@code RECONCILING}) record of a workshop — used by
     * the roster-finalization handler, which needs both states: {@code RECONCILING} to finalize and
     * {@code OPEN} as the recovery path (lost completion event).
     */
    List<AttendanceRecord> loadNonFinalizedByWorkshop(UUID workshopId);

    List<AttendanceRecord> loadByWorkshopId(UUID workshopId);

    /**
     * Lists the distinct workshop ids that still hold at least one non-finalized attendance record.
     * Used by the auto-finalize scheduler to discover workshops whose Reconciliation Window has
     * elapsed and that are safe to finalize.
     */
    List<UUID> getWorkshopIdsWithNonFinalizedRecords();
}