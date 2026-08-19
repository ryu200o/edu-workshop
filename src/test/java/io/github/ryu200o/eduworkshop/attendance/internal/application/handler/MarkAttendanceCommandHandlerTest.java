package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.RegistrationNotVerifiedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotInSessionException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.MarkAttendanceCommand;
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
import java.util.List;
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
class MarkAttendanceCommandHandlerTest {

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
    private static final Actor TRAINER = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);
    private static final UUID STUDENT_1 = UUID.randomUUID();
    private static final UUID STUDENT_2 = UUID.randomUUID();

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private MarkAttendanceCommandHandler handler() {
        return new MarkAttendanceCommandHandler(workshopExposeApi, registrationExposeApi,
                attendanceRecordRepository, attendanceDomainEventPublisher, clock);
    }

    private WorkshopSchedulingContract inProgressWorkshop() {
        return new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.IN_PROGRESS, null);
    }

    private MarkAttendanceCommand command(Actor actor) {
        return new MarkAttendanceCommand(WORKSHOP_ID, List.of(
                new MarkAttendanceCommand.MarkItem(STUDENT_1, AttendanceResult.PRESENT, null),
                new MarkAttendanceCommand.MarkItem(STUDENT_2, AttendanceResult.LATE, "arrived 10:20")), actor);
    }

    @Test
    void happyPath_createsRecordsForNewStudentsAndMarksExistingOnes() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_1)).thenReturn(true);
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_2)).thenReturn(true);
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT_1))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT_2))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(command(TRAINER));

        verify(attendanceRecordRepository).save(argThat(r ->
                r.state() == AttendanceState.OPEN && r.studentId().value().equals(STUDENT_1)));
        verify(attendanceRecordRepository).save(argThat(r ->
                r.state() == AttendanceState.OPEN && r.studentId().value().equals(STUDENT_2)));
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 2
                && events.stream().allMatch(e -> e instanceof AttendanceMarked)));
    }

    @Test
    void existingRecordIsCorrected_notDuplicated() {
        AttendanceRecord existing = AttendanceRecord.create(AttendanceRecordId.generate(),
                StudentId.of(STUDENT_1), WORKSHOP_ID, AttendanceResult.PRESENT, null, TRAINER, NOW);
        existing.clearDomainEvents();
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_1)).thenReturn(true);
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT_1))
                .thenReturn(Optional.of(existing));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler().handle(new MarkAttendanceCommand(WORKSHOP_ID, List.of(
                new MarkAttendanceCommand.MarkItem(STUDENT_1, AttendanceResult.ABSENT, "left early")), TRAINER));

        assertThat(existing.currentResult()).isEqualTo(AttendanceResult.ABSENT);
        assertThat(existing.entries()).hasSize(2);
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 1));
    }

    @Test
    void rejectsNonVerifiedStudent_failFastAtomic_overWholeBatch() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_1)).thenReturn(true);
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_2)).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(command(TRAINER)))
                .isInstanceOf(RegistrationNotVerifiedException.class)
                .satisfies(ex -> assertThat(((RegistrationNotVerifiedException) ex).studentIds())
                        .containsExactly(STUDENT_2));

        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(attendanceDomainEventPublisher);
    }

    @Test
    void persistenceFailureMidBatch_abortsWholeBatchWithoutPublishing() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_1)).thenReturn(true);
        when(registrationExposeApi.isVerified(WORKSHOP_ID, STUDENT_2)).thenReturn(true);
        when(attendanceRecordRepository.loadByWorkshopAndStudent(WORKSHOP_ID, STUDENT_1))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> handler().handle(command(TRAINER)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // Nothing was published — the whole batch rolled back (ADR 0019 §4 atomicity).
        verify(attendanceDomainEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsWhenWorkshopNotFound() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(command(TRAINER)))
                .isInstanceOf(ReferencedWorkshopNotFoundException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void rejectsWhenWorkshopNotInProgress() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.PUBLISHED, null)));

        assertThatThrownBy(() -> handler().handle(command(TRAINER)))
                .isInstanceOf(WorkshopNotInSessionException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void rejectsNonTrainerActor() {
        Actor student = new Actor(ActorId.of(STUDENT_1), ActorRole.STUDENT);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.of(inProgressWorkshop()));

        assertThatThrownBy(() -> handler().handle(command(student)))
                .isInstanceOf(AttendanceRoleViolationException.class);

        verifyNoInteractions(registrationExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }
}