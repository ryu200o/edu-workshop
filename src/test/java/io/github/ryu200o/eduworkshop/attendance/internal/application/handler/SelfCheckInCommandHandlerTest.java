package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.DuplicateAttendanceException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.RegistrationNotVerifiedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotInSessionException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.SelfCheckInCommand;
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
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceMarked;
import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfCheckInCommandHandlerTest {

    @Mock
    private WorkshopExposeAPI workshopExposeApi;

    @Mock
    private RegistrationExposeAPI registrationExposeApi;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceDomainEventPublisher attendanceDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final Actor STUDENT_ACTOR = new Actor(ActorId.of(STUDENT), ActorRole.STUDENT);
    private static final String QR_REFERENCE = "QR-REF-2026-09-01-ws";

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private SelfCheckInCommandHandler handler() {
        return new SelfCheckInCommandHandler(workshopExposeApi, registrationExposeApi,
                attendanceRecordRepository, attendanceDomainEventPublisher, clock);
    }

    private WorkshopSchedulingContract inProgressWorkshop() {
        return new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.IN_PROGRESS, null);
    }

    private SelfCheckInCommand command() {
        return new SelfCheckInCommand(WORKSHOP_ID, QR_REFERENCE, STUDENT_ACTOR);
    }

    private void stubGates() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT)).thenReturn(true);
    }

    @Test
    void verifiedStudent_inProgress_evaluatedAttended_createsPresentRecordAndPublishes() {
        stubGates();
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT))
                .thenReturn(Optional.empty());
        when(workshopExposeApi.evaluateCheckIn(WORKSHOP_ID, NOW)).thenReturn(Optional.of(AttendanceStatusContract.ATTENDED));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SelfCheckInCommand.Result result = handler().handle(command());

        assertThat(result.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(result.result()).isEqualTo(AttendanceResult.PRESENT);
        verify(attendanceRecordRepository).save(argThat(record ->
                record.state() == AttendanceState.OPEN
                        && record.currentResult() == AttendanceResult.PRESENT
                        && record.studentId().value().equals(STUDENT)
                        && record.entries().get(0).actor().role() == ActorRole.STUDENT));
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 1
                && events.get(0) instanceof AttendanceMarked
                && ((AttendanceMarked) events.get(0)).result() == AttendanceResult.PRESENT));
    }

    @Test
    void verifiedStudent_inProgress_evaluatedLate_createsLateRecord() {
        stubGates();
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT))
                .thenReturn(Optional.empty());
        when(workshopExposeApi.evaluateCheckIn(WORKSHOP_ID, NOW)).thenReturn(Optional.of(AttendanceStatusContract.LATE));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SelfCheckInCommand.Result result = handler().handle(command());

        assertThat(result.result()).isEqualTo(AttendanceResult.LATE);
        verify(attendanceRecordRepository).save(argThat(record ->
                record.currentResult() == AttendanceResult.LATE));
    }

    @Test
    void recordAlreadyExists_isIdempotentNoOp_neverMutatesNorPublishes() {
        AttendanceRecord existing = AttendanceRecord.create(AttendanceRecordId.generate(),
                StudentId.of(STUDENT), WORKSHOP_ID, AttendanceResult.PRESENT, null, STUDENT_ACTOR, NOW);
        existing.clearDomainEvents();
        stubGates();
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT))
                .thenReturn(Optional.of(existing));

        SelfCheckInCommand.Result result = handler().handle(command());

        // OQ-3B-3 idempotent no-op: returns the existing record's identity/result/state unchanged.
        assertThat(result.recordId()).isEqualTo(existing.id().value());
        assertThat(result.result()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(result.state()).isEqualTo(AttendanceState.OPEN);
        assertThat(existing.entries()).hasSize(1);          // no new ledger entry
        assertThat(existing.currentResult()).isEqualTo(AttendanceResult.PRESENT); // never flipped
        // No evaluation, no save, no publish — nothing at all was touched.
        verify(workshopExposeApi, never()).evaluateCheckIn(any(), any());
        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(attendanceDomainEventPublisher);
    }

    @Test
    void nonVerifiedStudent_isRejectedFailFast() {
        stubGates();
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT)).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(command()))
                .isInstanceOf(RegistrationNotVerifiedException.class);

        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(attendanceDomainEventPublisher);
    }

    @Test
    void workshopNotFound_isRejectedWith404() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(command()))
                .isInstanceOf(ReferencedWorkshopNotFoundException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void workshopNotInProgress_isRejectedWith409() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.PUBLISHED, null)));

        assertThatThrownBy(() -> handler().handle(command()))
                .isInstanceOf(WorkshopNotInSessionException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void nonStudentActor_isRejectedWith403() {
        Actor trainer = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));

        assertThatThrownBy(() -> handler().handle(new SelfCheckInCommand(WORKSHOP_ID, QR_REFERENCE, trainer)))
                .isInstanceOf(AttendanceRoleViolationException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void raceDoubleScan_uniqueBackstop_propagatesDuplicateAndPublishesNothing() {
        stubGates();
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT))
                .thenReturn(Optional.empty());
        when(workshopExposeApi.evaluateCheckIn(WORKSHOP_ID, NOW)).thenReturn(Optional.of(AttendanceStatusContract.ATTENDED));
        // Simulate the second concurrent writer: the save hits the DB unique backstop
        // (uq_student_workshop) and the write adapter translates it into the business exception.
        when(attendanceRecordRepository.save(any())).thenThrow(new DuplicateAttendanceException(WORKSHOP_ID, STUDENT));

        assertThatThrownBy(() -> handler().handle(command()))
                .isInstanceOf(DuplicateAttendanceException.class);

        verify(attendanceDomainEventPublisher, never()).publish(any());
    }

    @Test
    void blankQrReference_isRejectedAsMalformed() {
        assertThatThrownBy(() -> new SelfCheckInCommand(WORKSHOP_ID, "  ", STUDENT_ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
