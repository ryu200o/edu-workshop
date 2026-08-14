package io.github.ryu200o.eduworkshop.attendance.internal.application.handler;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.ReferencedWorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotCompletedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceEntry;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecord;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceRecordId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceRecordFinalized;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.MissingReconciliationAnchorException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowNotElapsedException;
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
class FinalizeWorkshopRosterCommandHandlerTest {

    @Mock
    private WorkshopExposeAPI workshopExposeApi;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceDomainEventPublisher attendanceDomainEventPublisher;

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final int WINDOW_MINUTES = 1440;
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Actor SYSTEM = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.SYSTEM);

    private final AttendanceReconciliationParameters parameters =
            new AttendanceReconciliationParameters(WINDOW_MINUTES);

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private FinalizeWorkshopRosterCommandHandler handler() {
        return new FinalizeWorkshopRosterCommandHandler(workshopExposeApi, attendanceRecordRepository,
                attendanceDomainEventPublisher, parameters, clock);
    }

    private AttendanceRecord openRecord(UUID id, Instant createdAt) {
        AttendanceRecord record = AttendanceRecord.create(AttendanceRecordId.of(id), StudentId.of(UUID.randomUUID()),
                WORKSHOP_ID, AttendanceResult.PRESENT, null, SYSTEM, createdAt);
        record.clearDomainEvents();
        return record;
    }

    private AttendanceRecord reconcilingRecord(UUID id, Instant completedAt) {
        AttendanceRecord record = openRecord(id, completedAt);
        record.beginReconciliation(completedAt, completedAt);
        return record;
    }

    @Test
    void happyPath_finalizesReconcilingRecordsAfterWindowElapsed() {
        Instant completedAt = NOW.minusSeconds(25 * 3600);
        AttendanceRecord record1 = reconcilingRecord(UUID.randomUUID(), completedAt);
        AttendanceRecord record2 = reconcilingRecord(UUID.randomUUID(), completedAt);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.COMPLETED, completedAt)));
        when(attendanceRecordRepository.loadNonFinalizedByWorkshop(WORKSHOP_ID)).thenReturn(List.of(record1, record2));

        FinalizeWorkshopRosterCommand.Result result = handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM));

        assertThat(result.finalizedRecordsCount()).isEqualTo(2);
        assertThat(result.state()).isEqualTo(AttendanceState.FINALIZED.name());
        assertThat(result.finalizedAt()).isEqualTo(NOW);
        assertThat(record1.state()).isEqualTo(AttendanceState.FINALIZED);
        assertThat(record2.state()).isEqualTo(AttendanceState.FINALIZED);
        verify(attendanceRecordRepository).saveAll(any());
        verify(attendanceDomainEventPublisher).publish(argThat(events -> events.size() == 2
                && events.stream().allMatch(e -> e instanceof AttendanceRecordFinalized)));
    }

    @Test
    void recoveryPath_reconcilesOpenRecordsAnchoredToAuthoritativeCompletedAt() {
        Instant completedAt = NOW.minusSeconds(25 * 3600);
        AttendanceRecord staleOpen = openRecord(UUID.randomUUID(), completedAt);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.COMPLETED, completedAt)));
        when(attendanceRecordRepository.loadNonFinalizedByWorkshop(WORKSHOP_ID)).thenReturn(List.of(staleOpen));

        handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM));

        assertThat(staleOpen.state()).isEqualTo(AttendanceState.FINALIZED);
        assertThat(staleOpen.reconciliationStartedAt()).isEqualTo(completedAt);
    }

    @Test
    void rejectsWhenWindowNotElapsed() {
        Instant completedAt = NOW.minusSeconds(600);
        AttendanceRecord record = reconcilingRecord(UUID.randomUUID(), completedAt);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(WORKSHOP_ID, WorkshopStateContract.COMPLETED, completedAt)));
        when(attendanceRecordRepository.loadNonFinalizedByWorkshop(WORKSHOP_ID)).thenReturn(List.of(record));

        assertThatThrownBy(() -> handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM)))
                .isInstanceOf(ReconciliationWindowNotElapsedException.class);

        verify(attendanceRecordRepository, never()).saveAll(any());
    }

    @Test
    void rejectsWhenWorkshopNotFound() {
        when(workshopExposeApi.getScheduling(WORKSHOP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM)))
                .isInstanceOf(ReferencedWorkshopNotFoundException.class);

        verifyNoInteractions(attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void rejectsWhenWorkshopNotCompleted() {
        Instant scheduled = NOW.minusSeconds(7200);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(
                        WORKSHOP_ID, WorkshopStateContract.PUBLISHED, scheduled)));

        assertThatThrownBy(() -> handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM)))
                .isInstanceOf(WorkshopNotCompletedException.class)
                .hasMessageContaining("not completed");

        verifyNoInteractions(attendanceRecordRepository, attendanceDomainEventPublisher);
    }

    @Test
    void rejectsReconcilingRecordWithMissingAnchor() {
        Instant completedAt = NOW.minusSeconds(25 * 3600);
        AttendanceRecord corrupted = AttendanceRecord.reconstruct(
                AttendanceRecordId.of(UUID.randomUUID()), StudentId.of(UUID.randomUUID()), WORKSHOP_ID,
                null, AttendanceResult.PRESENT, AttendanceState.RECONCILING,
                List.of(new AttendanceEntry(1, completedAt, SYSTEM, AttendanceAction.MARK,
                        AttendanceResult.PRESENT, null, null)),
                completedAt, completedAt);
        when(workshopExposeApi.getScheduling(WORKSHOP_ID))
                .thenReturn(Optional.of(new WorkshopSchedulingContract(
                        WORKSHOP_ID, WorkshopStateContract.COMPLETED, completedAt)));
        when(attendanceRecordRepository.loadNonFinalizedByWorkshop(WORKSHOP_ID)).thenReturn(List.of(corrupted));

        assertThatThrownBy(() -> handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, SYSTEM)))
                .isInstanceOf(MissingReconciliationAnchorException.class);

        verify(attendanceRecordRepository, never()).saveAll(any());
    }

    @Test
    void rejectsNonSystemActor() {
        Actor trainer = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);

        assertThatThrownBy(() -> handler().handle(new FinalizeWorkshopRosterCommand(WORKSHOP_ID, trainer)))
                .isInstanceOf(AttendanceRoleViolationException.class);

        verifyNoInteractions(workshopExposeApi, attendanceRecordRepository, attendanceDomainEventPublisher);
    }
}