package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SubmitAppealCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceAppealSubmitted;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.MissingReconciliationAnchorException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowExceededException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitAppealCommandHandlerTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceDomainEventPublisher attendanceDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final int WINDOW_MINUTES = 1440;
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final Actor STUDENT = new Actor(ActorId.of(STUDENT_ID), ActorRole.STUDENT);

    private final AttendanceReconciliationParameters parameters =
            new AttendanceReconciliationParameters(WINDOW_MINUTES);

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private SubmitAppealCommandHandler handler() {
        return new SubmitAppealCommandHandler(attendanceRecordRepository, attendanceDomainEventPublisher,
                parameters, clock);
    }

    private AttendanceRecord reconcilingRecord(Instant completedAt) {
        AttendanceRecord record = AttendanceRecord.create(AttendanceRecordId.of(RECORD_ID),
                StudentId.of(STUDENT_ID), UUID.randomUUID(), AttendanceResult.PRESENT, null, STUDENT, completedAt);
        record.clearDomainEvents();
        record.beginReconciliation(completedAt, completedAt);
        return record;
    }

    @Test
    void happyPath_recordsAppealWithoutChangingResult() {
        AttendanceRecord record = reconcilingRecord(NOW.minusSeconds(3600));
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAppealCommand.Result result = handler().handle(
                new SubmitAppealCommand(RECORD_ID, "I was actually present", "evidence://img-1", STUDENT));

        assertThat(result.recordId()).isEqualTo(RECORD_ID);
        assertThat(result.state()).isEqualTo(AttendanceState.RECONCILING.name());
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(record.entries()).hasSize(2);
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 1
                && events.getFirst() instanceof AttendanceAppealSubmitted));
    }

    @Test
    void rejectsWhenWindowExceeded() {
        AttendanceRecord record = reconcilingRecord(NOW.minusSeconds(3600));
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));

        Instant late = NOW.plusSeconds((WINDOW_MINUTES * 60) + 60);
        clock = Clock.fixed(late, ZoneOffset.UTC);

        assertThatThrownBy(() -> handler().handle(
                new SubmitAppealCommand(RECORD_ID, "late appeal", "evidence://img-2", STUDENT)))
                .isInstanceOf(ReconciliationWindowExceededException.class);

        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsMissingEvidenceReference() {
        AttendanceRecord record = reconcilingRecord(NOW.minusSeconds(3600));
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> handler().handle(
                new SubmitAppealCommand(RECORD_ID, "reason without evidence", null, STUDENT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");

        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsReconcilingRecordWithMissingAnchor() {
        AttendanceRecord record = AttendanceRecord.reconstruct(
                AttendanceRecordId.of(RECORD_ID), StudentId.of(STUDENT_ID), UUID.randomUUID(),
                null, AttendanceResult.PRESENT, AttendanceState.RECONCILING,
                List.of(new io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceEntry(
                        1, NOW, STUDENT, io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction.MARK,
                        AttendanceResult.PRESENT, null, null)),
                NOW, NOW);
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> handler().handle(
                new SubmitAppealCommand(RECORD_ID, "reason", "evidence://img-3", STUDENT)))
                .isInstanceOf(MissingReconciliationAnchorException.class);

        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsWhenRecordNotFound() {
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new SubmitAppealCommand(RECORD_ID, "reason", null, STUDENT)))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void rejectsNonStudentActor() {
        Actor trainer = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);

        assertThatThrownBy(() -> handler().handle(
                new SubmitAppealCommand(RECORD_ID, "reason", null, trainer)))
                .isInstanceOf(AttendanceRoleViolationException.class);

        verify(attendanceRecordRepository, never()).loadById(any());
    }
}