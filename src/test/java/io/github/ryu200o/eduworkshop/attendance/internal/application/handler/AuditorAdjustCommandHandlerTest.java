package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.AuditorAdjustCommand;
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
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AuditorAdjustedAttendance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class AuditorAdjustCommandHandlerTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceDomainEventPublisher attendanceDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final Actor AUDITOR = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.AUDITOR);
    private static final UUID AUDITOR_ID = AUDITOR.id().value();
    private static final Actor STUDENT = new Actor(ActorId.of(STUDENT_ID), ActorRole.STUDENT);

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private AuditorAdjustCommandHandler handler() {
        return new AuditorAdjustCommandHandler(attendanceRecordRepository, attendanceDomainEventPublisher, clock);
    }

    private AttendanceRecord reconcilingRecord() {
        AttendanceRecord record = AttendanceRecord.create(AttendanceRecordId.of(RECORD_ID),
                StudentId.of(STUDENT_ID), UUID.randomUUID(), AttendanceResult.PRESENT, null, STUDENT,
                NOW.minusSeconds(7200));
        record.clearDomainEvents();
        record.beginReconciliation(NOW.minusSeconds(3600), NOW.minusSeconds(3600));
        return record;
    }

    @Test
    void happyPath_adjustsCurrentResultAndAppendsLedgerEntry() {
        AttendanceRecord record = reconcilingRecord();
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(
                new AuditorAdjustCommand(RECORD_ID, AttendanceResult.ABSENT, "student marked absent per CCTV",
                        "evidence://cam-2", AUDITOR_ID));

        assertThat(record.state()).isEqualTo(AttendanceState.RECONCILING);
        assertThat(record.currentResult()).isEqualTo(AttendanceResult.ABSENT);
        assertThat(record.entries()).hasSize(2);
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 1
                && events.getFirst() instanceof AuditorAdjustedAttendance));
    }

    @Test
    void rejectsWhenJustificationMissing() {
        AttendanceRecord record = reconcilingRecord();
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> handler().handle(
                new AuditorAdjustCommand(RECORD_ID, AttendanceResult.ABSENT, "  ", null, AUDITOR_ID)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsWhenEvidenceMissing() {
        AttendanceRecord record = reconcilingRecord();
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> handler().handle(
                new AuditorAdjustCommand(RECORD_ID, AttendanceResult.ABSENT, "reason", null, AUDITOR_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceReference");

        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void rejectsWhenRecordNotFound() {
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new AuditorAdjustCommand(RECORD_ID, AttendanceResult.ABSENT, "reason", null, AUDITOR_ID)))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void handlerAssignsAuditorRole_regardlessOfPrincipal() {
        AttendanceRecord record = reconcilingRecord();
        when(attendanceRecordRepository.loadById(any())).thenReturn(Optional.of(record));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // A non-AUDITOR principal (e.g. a STUDENT who passed @CanAuditAttendance via ADMIN) is
        // assigned the contextual AUDITOR role by the handler — the role is a use-case concern, not
        // the caller's identity (ADR 0023).
        handler().handle(new AuditorAdjustCommand(RECORD_ID, AttendanceResult.ABSENT, "reason", "evidence://cam-9",
                STUDENT.id().value()));

        assertThat(record.entries()).hasSize(2);
        assertThat(record.entries().get(1).actor().role()).isEqualTo(ActorRole.AUDITOR);
    }
}