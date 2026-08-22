package io.github.ryu200o.eduworkshop.attendance.internal.domain;

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
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceAppealSubmitted;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceMarked;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceRecordFinalized;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AuditorAdjustedAttendance;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceRecordFinalizedException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceRecordOwnershipViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceStateException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.InvalidActorRoleException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowExceededException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowNotElapsedException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final StudentId STUDENT = StudentId.of(UUID.randomUUID());
    private static final Actor TRAINER = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);
    private static final Actor STUDENT_ACTOR = new Actor(ActorId.of(STUDENT.value()), ActorRole.STUDENT);
    private static final Actor AUDITOR = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.AUDITOR);
    private static final Actor SYSTEM = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.SYSTEM);
    private static final Duration WINDOW = Duration.ofHours(24);

    private AttendanceRecord openedRecord() {
        return AttendanceRecord.create(AttendanceRecordId.generate(), STUDENT, WORKSHOP_ID,
                AttendanceResult.PRESENT, null, TRAINER, NOW);
    }

    private AttendanceRecord reconcilingRecord(Instant completedAt, Instant now) {
        AttendanceRecord record = openedRecord();
        record.beginReconciliation(completedAt, now);
        return record;
    }

    private AttendanceRecord finalizedRecord() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        record.finalizeRecord(SYSTEM, NOW.plus(WINDOW), NOW.plus(WINDOW));
        return record;
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Test
    void create_opensRecordInOpenStateWithFirstMarkEntry() {
        AttendanceRecordId id = AttendanceRecordId.generate();
        AttendanceRecord record = AttendanceRecord.create(id, STUDENT, WORKSHOP_ID,
                AttendanceResult.PRESENT, "on time", TRAINER, NOW);

        assertThat(record.id()).isEqualTo(id);
        assertThat(record.studentId()).isEqualTo(STUDENT);
        assertThat(record.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(record.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(record.reconciliationStartedAt()).isNull();
        assertThat(record.createdAt()).isEqualTo(NOW);
        assertThat(record.updatedAt()).isEqualTo(NOW);

        assertThat(record.entries()).hasSize(1);
        AttendanceEntry first = record.entries().get(0);
        assertThat(first.entryNumber()).isEqualTo(1);
        assertThat(first.action()).isEqualTo(AttendanceAction.MARK);
        assertThat(first.result()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(first.actor()).isEqualTo(TRAINER);
        assertThat(first.timestamp()).isEqualTo(NOW);
        assertThat(first.reason()).isEqualTo("on time");

        assertThat(record.recordedEvents())
                .hasSize(1)
                .hasOnlyElementsOfType(AttendanceMarked.class);
    }

    @Test
    void create_rejectsNullArguments() {
        assertThatThrownBy(() -> AttendanceRecord.create(null, STUDENT, WORKSHOP_ID,
                AttendanceResult.PRESENT, null, TRAINER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRecord.create(AttendanceRecordId.generate(), null, WORKSHOP_ID,
                AttendanceResult.PRESENT, null, TRAINER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRecord.create(AttendanceRecordId.generate(), STUDENT, null,
                AttendanceResult.PRESENT, null, TRAINER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRecord.create(AttendanceRecordId.generate(), STUDENT, WORKSHOP_ID,
                null, null, TRAINER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRecord.create(AttendanceRecordId.generate(), STUDENT, WORKSHOP_ID,
                AttendanceResult.PRESENT, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRecord.create(AttendanceRecordId.generate(), STUDENT, WORKSHOP_ID,
                AttendanceResult.PRESENT, null, TRAINER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    // markAttendance (OPEN only — workshop IN_PROGRESS)
    // ----------------------------------------------------------------

    @Test
    void markAttendance_inOpenState_appendsEntryAndFlipsCurrentResult() {
        AttendanceRecord record = openedRecord();

        record.markAttendance(AttendanceResult.LATE, "arrived 10 min late", TRAINER, NOW.plusSeconds(60));

        assertThat(record.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.LATE);
        assertThat(record.entries()).hasSize(2);
        AttendanceEntry mark = record.entries().get(1);
        assertThat(mark.entryNumber()).isEqualTo(2);
        assertThat(mark.action()).isEqualTo(AttendanceAction.MARK);
        assertThat(mark.result()).isEqualTo(AttendanceResult.LATE);
        assertThat(record.updatedAt()).isEqualTo(NOW.plusSeconds(60));

        assertThat(record.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(AttendanceMarked.class, AttendanceMarked.class);
    }

    @Test
    void markAttendance_repeatedCorrections_areAllowedAsLedgerEntries() {
        AttendanceRecord record = openedRecord();
        record.markAttendance(AttendanceResult.ABSENT, null, TRAINER, NOW.plusSeconds(120));
        record.markAttendance(AttendanceResult.PRESENT, "late correction", TRAINER, NOW.plusSeconds(180));

        assertThat(record.entries()).hasSize(3);
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(record.entries().get(2).entryNumber()).isEqualTo(3);
    }

    @Test
    void markAttendance_inReconcilingState_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        assertThatThrownBy(() -> record.markAttendance(AttendanceResult.PRESENT, null, TRAINER, NOW))
                .isInstanceOf(AttendanceStateException.class);
    }

    @Test
    void markAttendance_byNonTrainerActor_isRejected() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.markAttendance(AttendanceResult.PRESENT, null, STUDENT_ACTOR, NOW))
                .isInstanceOf(InvalidActorRoleException.class);
    }

    // ----------------------------------------------------------------
    // submitAppeal (RECONCILING only — never mutates currentResult)
    // ----------------------------------------------------------------

    @Test
    void submitAppeal_inReconcilingState_recordsRequestWithoutChangingResult() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        record.submitAppeal("I was actually present", "photo://entry-cam-42",
                STUDENT_ACTOR, NOW.plus(WINDOW), NOW.plusSeconds(30));

        assertThat(record.state()).isEqualTo(AttendanceState.RECONCILING);
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.PRESENT); // unchanged
        assertThat(record.entries()).hasSize(2);
        AttendanceEntry appeal = record.entries().get(1);
        assertThat(appeal.action()).isEqualTo(AttendanceAction.APPEAL);
        assertThat(appeal.result()).isEqualTo(AttendanceResult.PRESENT); // informational = current
        assertThat(appeal.actor()).isEqualTo(STUDENT_ACTOR);
        assertThat(appeal.evidenceReference()).isEqualTo("photo://entry-cam-42");

        assertThat(record.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(AttendanceMarked.class, AttendanceAppealSubmitted.class);
    }

    @Test
    void submitAppeal_inOpenState_isRejected() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.submitAppeal("appeal", null, STUDENT_ACTOR,
                NOW.plus(WINDOW), NOW))
                .isInstanceOf(AttendanceStateException.class);
    }

    @Test
    void submitAppeal_byNonStudentActor_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        assertThatThrownBy(() -> record.submitAppeal("appeal", "evidence://img-1", TRAINER,
                NOW.plus(WINDOW), NOW))
                .isInstanceOf(InvalidActorRoleException.class);
    }

    @Test
    void submitAppeal_byDifferentStudent_isRejectedAsOwnershipViolation() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Actor otherStudent = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.STUDENT);

        assertThatThrownBy(() -> record.submitAppeal("appeal", "evidence://img-1", otherStudent,
                NOW.plus(WINDOW), NOW))
                .isInstanceOf(AttendanceRecordOwnershipViolationException.class);
    }

    @Test
    void submitAppeal_afterDeadline_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        assertThatThrownBy(() -> record.submitAppeal("appeal", "evidence://img-1", STUDENT_ACTOR,
                deadline, deadline.plusSeconds(1)))
                .isInstanceOf(ReconciliationWindowExceededException.class);
    }

    @Test
    void submitAppeal_exactlyAtDeadline_isAllowed() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        record.submitAppeal("appeal", "evidence://img-1", STUDENT_ACTOR, deadline, deadline);

        assertThat(record.entries()).hasSize(2);
    }

    @Test
    void submitAppeal_requiresMandatoryEvidence() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        assertThatThrownBy(() -> record.submitAppeal("appeal", null, STUDENT_ACTOR, deadline, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");
        assertThatThrownBy(() -> record.submitAppeal("appeal", "   ", STUDENT_ACTOR, deadline, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");
    }

    // ----------------------------------------------------------------
    // auditorAdjust (RECONCILING only — the authoritative mutation)
    // ----------------------------------------------------------------

    @Test
    void auditorAdjust_inReconcilingState_appendsEntryAndFlipsCurrentResult() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        record.auditorAdjust(AttendanceResult.PRESENT, "evidence confirms presence",
                "photo://entry-cam-42", AUDITOR, NOW.plusSeconds(60));

        assertThat(record.currentResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(record.entries()).hasSize(2);
        AttendanceEntry adjust = record.entries().get(1);
        assertThat(adjust.action()).isEqualTo(AttendanceAction.AUDITOR_ADJUST);
        assertThat(adjust.actor()).isEqualTo(AUDITOR);
        assertThat(adjust.reason()).isEqualTo("evidence confirms presence");

        assertThat(record.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(AttendanceMarked.class, AuditorAdjustedAttendance.class);
    }

    @Test
    void auditorAdjust_byNonAuditorActor_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.PRESENT, "reason",
                "evidence://img-1", TRAINER, NOW))
                .isInstanceOf(InvalidActorRoleException.class);
    }

    @Test
    void auditorAdjust_requiresMandatoryReason() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.PRESENT, "  ",
                "photo://entry-cam-42", AUDITOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditorAdjust_requiresMandatoryEvidence() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);

        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.PRESENT, "reason", null,
                AUDITOR, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");
        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.PRESENT, "reason", "   ",
                AUDITOR, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");
    }

    @Test
    void auditorAdjust_inOpenState_isRejected() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.PRESENT, "reason",
                null, AUDITOR, NOW))
                .isInstanceOf(AttendanceStateException.class);
    }

    // ----------------------------------------------------------------
    // beginReconciliation (anchored to WorkshopCompleted.completedAt)
    // ----------------------------------------------------------------

    @Test
    void beginReconciliation_flipsOpenToReconcilingWithCompletedAtAnchor() {
        AttendanceRecord record = openedRecord();
        Instant completedAt = NOW.plus(Duration.ofHours(2));

        record.beginReconciliation(completedAt, NOW);

        assertThat(record.state()).isEqualTo(AttendanceState.RECONCILING);
        assertThat(record.reconciliationStartedAt()).isEqualTo(completedAt);
        assertThat(record.entries()).hasSize(1); // no extra ledger entry
        assertThat(record.recordedEvents()).hasSize(1); // no extra event
    }

    @Test
    void beginReconciliation_isIdempotent_forOutboxReplay() {
        Instant completedAt = NOW.plus(Duration.ofHours(2));
        AttendanceRecord record = reconcilingRecord(completedAt, NOW);

        record.beginReconciliation(completedAt, NOW.plusSeconds(10));

        assertThat(record.state()).isEqualTo(AttendanceState.RECONCILING);
        assertThat(record.reconciliationStartedAt()).isEqualTo(completedAt);
        assertThat(record.entries()).hasSize(1);
        assertThat(record.recordedEvents()).hasSize(1);
    }

    @Test
    void beginReconciliation_isNoOp_whenAlreadyFinalized() {
        AttendanceRecord record = finalizedRecord();
        record.beginReconciliation(NOW.plus(Duration.ofHours(3)), NOW);

        assertThat(record.state()).isEqualTo(AttendanceState.FINALIZED);
        assertThat(record.reconciliationStartedAt()).isEqualTo(NOW);
    }

    // ----------------------------------------------------------------
    // finalizeRecord (only after the window has elapsed)
    // ----------------------------------------------------------------

    @Test
    void finalizeRecord_afterWindowElapsed_locksRecord() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        record.finalizeRecord(SYSTEM, deadline, deadline);

        assertThat(record.state()).isEqualTo(AttendanceState.FINALIZED);
        assertThat(record.entries()).hasSize(2);
        AttendanceEntry fin = record.entries().get(1);
        assertThat(fin.action()).isEqualTo(AttendanceAction.FINALIZE);
        assertThat(fin.actor()).isEqualTo(SYSTEM);
        assertThat(record.recordedEvents())
                .hasSize(2)
                .hasExactlyElementsOfTypes(AttendanceMarked.class, AttendanceRecordFinalized.class);
    }

    @Test
    void finalizeRecord_beforeWindowElapsed_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        assertThatThrownBy(() -> record.finalizeRecord(SYSTEM, deadline.minusSeconds(1), deadline))
                .isInstanceOf(ReconciliationWindowNotElapsedException.class);
    }

    @Test
    void finalizeRecord_inOpenState_isRejected() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.finalizeRecord(SYSTEM, NOW, NOW))
                .isInstanceOf(AttendanceStateException.class);
    }

    @Test
    void finalizeRecord_byNonSystemOrAuditorActor_isRejected() {
        AttendanceRecord record = reconcilingRecord(NOW, NOW);
        Instant deadline = NOW.plus(WINDOW);

        assertThatThrownBy(() -> record.finalizeRecord(TRAINER, deadline, deadline))
                .isInstanceOf(InvalidActorRoleException.class);
    }

    // ----------------------------------------------------------------
    // FINALIZED lock (guardFinalized — exact message)
    // ----------------------------------------------------------------

    @Test
    void finalizedRecord_rejectsEveryMutationWithExactLockMessage() {
        AttendanceRecord record = finalizedRecord();

        assertThatThrownBy(() -> record.markAttendance(AttendanceResult.ABSENT, null, TRAINER, NOW))
                .isInstanceOf(AttendanceRecordFinalizedException.class)
                .hasMessage("Attendance record is finalized and locked");
        assertThatThrownBy(() -> record.submitAppeal("appeal", null, STUDENT_ACTOR, NOW, NOW))
                .isInstanceOf(AttendanceRecordFinalizedException.class)
                .hasMessage("Attendance record is finalized and locked");
        assertThatThrownBy(() -> record.auditorAdjust(AttendanceResult.ABSENT, "reason", null, AUDITOR, NOW))
                .isInstanceOf(AttendanceRecordFinalizedException.class)
                .hasMessage("Attendance record is finalized and locked");
        assertThatThrownBy(() -> record.finalizeRecord(SYSTEM, NOW, NOW))
                .isInstanceOf(AttendanceRecordFinalizedException.class)
                .hasMessage("Attendance record is finalized and locked");
    }

    // ----------------------------------------------------------------
    // Read access — unmodifiable ledger & events
    // ----------------------------------------------------------------

    @Test
    void entries_isUnmodifiable() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.entries().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordedEvents_isUnmodifiable() {
        AttendanceRecord record = openedRecord();

        assertThatThrownBy(() -> record.recordedEvents().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ----------------------------------------------------------------
    // Renumbering — reconstruct keeps the next entry number contiguous
    // ----------------------------------------------------------------

    @Test
    void reconstruct_continuesRenumberingFromRehydratedEntries() {
        AttendanceRecord rehydrated = AttendanceRecord.reconstruct(
                AttendanceRecordId.generate(), STUDENT, WORKSHOP_ID, NOW,
                AttendanceResult.PRESENT, AttendanceState.OPEN,
                List.of(
                        new AttendanceEntry(1, NOW, TRAINER, AttendanceAction.MARK,
                                AttendanceResult.PRESENT, null, null),
                        new AttendanceEntry(2, NOW, TRAINER, AttendanceAction.MARK,
                                AttendanceResult.LATE, "correction", null)),
                NOW, NOW);

        // Rehydration must NOT replay history as domain events — only the single NEW event
        // produced by the subsequent markAttendance is recorded (ADR 0019 §6 / reviewer req.).
        assertThat(rehydrated.recordedEvents()).isEmpty();

        rehydrated.markAttendance(AttendanceResult.ABSENT, null, TRAINER, NOW.plusSeconds(1));

        assertThat(rehydrated.entries()).hasSize(3);
        assertThat(rehydrated.entries().get(2).entryNumber()).isEqualTo(3);
        assertThat(rehydrated.recordedEvents()).hasSize(1);
    }
}