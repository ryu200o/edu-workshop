package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceAppealSubmitted;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceMarked;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceRecordFinalized;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AuditorAdjustedAttendance;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceRecordFinalizedException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceStateException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowExceededException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowNotElapsedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root representing a learner's attendance for a workshop — an <em>append-only Decision
 * Ledger</em> (ADR 0019). The master holds the materialized current state
 * ({@code currentResult}, {@code state}) plus the reconciliation anchor
 * ({@code reconciliationStartedAt} = {@code WorkshopCompleted.completedAt}); every decision
 * (MARK / APPEAL / AUDITOR_ADJUST / FINALIZE) is appended to the ledger and never updated or
 * removed.
 *
 * <p><strong>4 core semantics (ADR 0019 v2, SA directive):</strong></p>
 * <ol>
 *   <li>{@code Workshop.state} is the authority for the attendance lifecycle — the domain never
 *       infers lifecycle from time.</li>
 *   <li>{@code WorkshopCompleted.completedAt} is the temporal anchor of the Reconciliation Window;
 *       the window itself is an Operational Setting supplied by the Application layer.</li>
 *   <li>Student Appeal <em>never</em> changes {@code currentResult} — it only records the request
 *       and evidence.</li>
 *   <li>{@code auditorAdjust()} is the only authoritative mutation during Reconciliation.</li>
 * </ol>
 *
 * <p>The <em>global</em> rules (workshop exists &amp; {@code IN_PROGRESS}, learner's registration is
 * {@code VERIFIED}) are set-based and therefore orchestrated by the Application handler (ADR 0005);
 * the domain enforces only local invariants and never receives a repository or policy parameter.</p>
 */
public class AttendanceRecord {

    private final AttendanceRecordId id;
    private final StudentId studentId;
    private final UUID workshopId;
    private Instant reconciliationStartedAt;
    private AttendanceResult currentResult;
    private AttendanceState state;
    private final List<AttendanceEntry> entries = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;

    private List<AttendanceDomainEvent> recordedEvents = new ArrayList<>();

    private AttendanceRecord(AttendanceRecordId id,
                             StudentId studentId,
                             UUID workshopId,
                             Instant reconciliationStartedAt,
                             AttendanceResult currentResult,
                             AttendanceState state,
                             Instant createdAt,
                             Instant updatedAt) {
        this.id = requireNonNull(id, "AttendanceRecordId cannot be null");
        this.studentId = requireNonNull(studentId, "StudentId cannot be null");
        this.workshopId = requireNonNull(workshopId, "workshopId cannot be null");
        this.reconciliationStartedAt = reconciliationStartedAt;
        this.currentResult = requireNonNull(currentResult, "currentResult cannot be null");
        this.state = requireNonNull(state, "state cannot be null");
        this.createdAt = requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    /**
     * Opens a fresh attendance record for a learner while the workshop is {@code IN_PROGRESS}. The
     * record is born {@code OPEN} with its first ledger entry {@code MARK} and
     * {@code currentResult} equal to the first recorded result.
     *
     * <p>The {@code VERIFIED} registration gate is a global rule and is enforced by the Application
     * handler before this factory is called.</p>
     */
    public static AttendanceRecord create(AttendanceRecordId id,
                                          StudentId studentId,
                                          UUID workshopId,
                                          AttendanceResult firstResult,
                                          String note,
                                          Actor actor,
                                          Instant now) {
        requireNonNull(id, "AttendanceRecordId cannot be null");
        requireNonNull(studentId, "StudentId cannot be null");
        requireNonNull(workshopId, "workshopId cannot be null");
        requireNonNull(firstResult, "firstResult cannot be null");
        requireNonNull(actor, "actor cannot be null");
        requireNonNull(now, "now cannot be null");

        AttendanceRecord record = new AttendanceRecord(id, studentId, workshopId, null, firstResult,
                AttendanceState.OPEN, now, now);
        record.appendEntry(AttendanceAction.MARK, firstResult, note, null, actor, now);
        record.record(new AttendanceMarked(id, studentId, workshopId, firstResult, now));
        return record;
    }

    /**
     * Reconstitutes an existing attendance record from persistence. Bypasses all invariant checks (no
     * spurious re-validation on read), mirroring {@code Registration.reconstruct}.
     */
    public static AttendanceRecord reconstruct(AttendanceRecordId id,
                                               StudentId studentId,
                                               UUID workshopId,
                                               Instant reconciliationStartedAt,
                                               AttendanceResult currentResult,
                                               AttendanceState state,
                                               List<AttendanceEntry> entries,
                                               Instant createdAt,
                                               Instant updatedAt) {
        requireNonNull(id, "AttendanceRecordId cannot be null");
        requireNonNull(studentId, "StudentId cannot be null");
        requireNonNull(workshopId, "workshopId cannot be null");
        requireNonNull(currentResult, "currentResult cannot be null");
        requireNonNull(state, "state cannot be null");
        requireNonNull(entries, "entries cannot be null");

        AttendanceRecord record = new AttendanceRecord(id, studentId, workshopId,
                reconciliationStartedAt, currentResult, state, createdAt, updatedAt);
        record.entries.addAll(entries);
        return record;
    }

    // ---------------------------------------------------------------------
    // Behaviors (State Matrix — ADR 0019 §9)
    // ---------------------------------------------------------------------

    /**
     * Marks (or corrects) a learner's attendance while the workshop is {@code IN_PROGRESS}
     * (record {@code OPEN}). Appends a {@code MARK} entry and flips {@code currentResult}.
     * Repeated corrections are allowed (each is a ledger entry).
     */
    public void markAttendance(AttendanceResult result, String note, Actor actor, Instant now) {
        guardFinalized();
        requireState(AttendanceState.OPEN, "mark attendance");

        appendEntry(AttendanceAction.MARK, result, note, null, actor, now);
        this.currentResult = result;
        this.touch(now);

        record(new AttendanceMarked(id, studentId, workshopId, result, now));
    }

    /**
     * Submits a student appeal (request + evidence) during the Reconciliation Window.
     *
     * <p><strong>Appeal never changes {@code currentResult}</strong> (ADR 0019 §5): the APPEAL entry
     * is appended with the current result for information only; only {@link #auditorAdjust} is an
     * authoritative mutation.</p>
     *
     * @throws ReconciliationWindowExceededException if {@code now > reconciliationDeadline}
     */
    public void submitAppeal(String reason,
                             String evidenceReference,
                             Actor actor,
                             Instant reconciliationDeadline,
                             Instant now) {
        guardFinalized();
        requireState(AttendanceState.RECONCILING, "submit an appeal");
        requireNonNull(reason, "reason cannot be null");
        requireNonNull(actor, "actor cannot be null");
        requireNonNull(reconciliationDeadline, "reconciliationDeadline cannot be null");
        requireNonNull(now, "now cannot be null");

        if (now.isAfter(reconciliationDeadline)) {
            throw new ReconciliationWindowExceededException(id, reconciliationDeadline, now);
        }

        appendEntry(AttendanceAction.APPEAL, currentResult, reason, evidenceReference, actor, now);
        this.touch(now);

        record(new AttendanceAppealSubmitted(id, studentId, workshopId, reason, now));
    }

    /**
     * The only authoritative mutation during Reconciliation: an auditor adjusts the outcome. Appends
     * an {@code AUDITOR_ADJUST} entry and flips {@code currentResult}. A justification is mandatory.
     *
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public void auditorAdjust(AttendanceResult result,
                              String reason,
                              String evidenceReference,
                              Actor actor,
                              Instant now) {
        guardFinalized();
        requireState(AttendanceState.RECONCILING, "adjust attendance");
        requireNonNull(result, "result cannot be null");
        requireNonNull(actor, "actor cannot be null");
        requireNonNull(now, "now cannot be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is mandatory for an auditor adjustment");
        }

        appendEntry(AttendanceAction.AUDITOR_ADJUST, result, reason, evidenceReference, actor, now);
        this.currentResult = result;
        this.touch(now);

        record(new AuditorAdjustedAttendance(id, studentId, workshopId, result, now));
    }

    /**
     * Opens the Reconciliation Window ({@code OPEN → RECONCILING}) when the workshop is completed.
     * {@code reconciliationStartedAt} is anchored to {@code WorkshopCompleted.completedAt}
     * (ADR 0019 §4) — never inferred from {@code now}.
     *
     * <p><strong>Idempotent (outbox replay-safe):</strong> a record already {@code RECONCILING} or
     * {@code FINALIZED} is left untouched, so a re-delivered
     * {@code WorkshopCompletedIntegrationEvent} is a safe no-op.</p>
     */
    public void beginReconciliation(Instant completedAt, Instant now) {
        requireNonNull(completedAt, "completedAt cannot be null");
        requireNonNull(now, "now cannot be null");

        if (state == AttendanceState.OPEN) {
            this.reconciliationStartedAt = completedAt;
            this.state = AttendanceState.RECONCILING;
            this.touch(now);
        }
    }

    /**
     * Finalizes the record ({@code RECONCILING → FINALIZED}) once the Reconciliation Window has
     * closed ({@code now >= reconciliationDeadline}). The record is then permanently locked.
     *
     * @throws ReconciliationWindowNotElapsedException if the window has not yet closed
     */
    public void finalizeRecord(Actor actor, Instant now, Instant reconciliationDeadline) {
        guardFinalized();
        requireState(AttendanceState.RECONCILING, "finalize the record");
        requireNonNull(actor, "actor cannot be null");
        requireNonNull(reconciliationDeadline, "reconciliationDeadline cannot be null");
        requireNonNull(now, "now cannot be null");

        if (now.isBefore(reconciliationDeadline)) {
            throw new ReconciliationWindowNotElapsedException(id, reconciliationDeadline, now);
        }

        appendEntry(AttendanceAction.FINALIZE, currentResult, null, null, actor, now);
        this.state = AttendanceState.FINALIZED;
        this.touch(now);

        record(new AttendanceRecordFinalized(id, studentId, workshopId, now));
    }

    // ---------------------------------------------------------------------
    // Guards
    // ---------------------------------------------------------------------

    private void guardFinalized() {
        if (state == AttendanceState.FINALIZED) {
            throw new AttendanceRecordFinalizedException("Attendance record is finalized and locked");
        }
    }

    private void requireState(AttendanceState expected, String operation) {
        if (state != expected) {
            throw new AttendanceStateException(id, state, expected);
        }
    }

    private void appendEntry(AttendanceAction action,
                             AttendanceResult result,
                             String reason,
                             String evidenceReference,
                             Actor actor,
                             Instant now) {
        int nextNumber = entries.size() + 1;
        entries.add(new AttendanceEntry(nextNumber, now, actor, action, result, reason, evidenceReference));
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private void record(AttendanceDomainEvent event) {
        recordedEvents.add(event);
    }

    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    public AttendanceRecordId id() {
        return id;
    }

    public StudentId studentId() {
        return studentId;
    }

    public UUID workshopId() {
        return workshopId;
    }

    public Instant reconciliationStartedAt() {
        return reconciliationStartedAt;
    }

    public AttendanceResult currentResult() {
        return currentResult;
    }

    public AttendanceState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Read-only view of the Decision Ledger, ordered by {@code entryNumber} ascending.
     */
    public List<AttendanceEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<AttendanceDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearDomainEvents() {
        recordedEvents = new ArrayList<>();
    }
}