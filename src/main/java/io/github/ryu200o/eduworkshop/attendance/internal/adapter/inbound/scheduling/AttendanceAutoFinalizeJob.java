package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.scheduling;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command.FinalizeWorkshopRosterCommand;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter.AttendanceReconciliationParameters;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordRepository;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopSchedulingContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Driving (inbound) scheduling adapter for the Attendance module — the Clock/Timer is a Driving
 * Actor on the same "north side" as an HTTP adapter. Runs periodically (fixed delay) to finalize
 * attendance rosters whose Reconciliation Window has elapsed, as a safety net behind manual
 * auditor finalization ({@code POST /api/v1/workshops/{workshopId}/attendance/finalize}).
 *
 * <p>Per scan it discovers workshops that still hold non-finalized records, filters to those
 * {@code COMPLETED} and past {@code completedAt + windowMinutes}, and dispatches a
 * {@link FinalizeWorkshopRosterCommand} with the {@link FinalizeWorkshopRosterCommand#SYSTEM_ACTOR_ID}
 * (the system identity). Every per-workshop failure is caught and logged so a single bad row never
 * interrupts the scheduled run. The handler re-asserts the domain guards (workshop completed, window
 * elapsed), so the job is a best-effort trigger, not an authority on eligibility.</p>
 *
 * <p>The bean is gated by {@code app.attendance.finalize-job.enabled} so deployments (and the test
 * suite, which disables it) can turn the periodic scanner off without code changes — when the
 * property is absent or {@code true} the bean is created and the {@code @Scheduled} scan runs.</p>
 */
@ConditionalOnProperty(name = "app.attendance.finalize-job.enabled", havingValue = "true", matchIfMissing = true)
@Component
class AttendanceAutoFinalizeJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAutoFinalizeJob.class);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final WorkshopExposeAPI workshopExposeApi;
    private final AttendanceReconciliationParameters reconciliationParameters;
    private final CommandBus commandBus;
    private final Clock clock;

    AttendanceAutoFinalizeJob(AttendanceRecordRepository attendanceRecordRepository,
                              WorkshopExposeAPI workshopExposeApi,
                              AttendanceReconciliationParameters reconciliationParameters,
                              CommandBus commandBus,
                              Clock clock) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.workshopExposeApi = workshopExposeApi;
        this.reconciliationParameters = reconciliationParameters;
        this.commandBus = commandBus;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.attendance.finalize-job.scan-interval-ms:60000}")
    void scan() {
        Instant now = Instant.now(clock);
        List<UUID> workshopIds = attendanceRecordRepository.getWorkshopIdsWithNonFinalizedRecords();
        for (UUID workshopId : workshopIds) {
            try {
                if (!isPastReconciliationWindow(workshopId, now)) {
                    continue;
                }
                commandBus.execute(new FinalizeWorkshopRosterCommand(
                        workshopId, FinalizeWorkshopRosterCommand.SYSTEM_ACTOR_ID));
                log.info("Auto-finalized attendance roster for workshop {}", workshopId);
            } catch (Exception e) {
                log.warn("Auto-finalize skipped for workshop {}: {}", workshopId, e.getMessage());
            }
        }
    }

    private boolean isPastReconciliationWindow(UUID workshopId, Instant now) {
        WorkshopSchedulingContract workshop = workshopExposeApi.getScheduling(workshopId).orElse(null);
        if (workshop == null || workshop.state() != WorkshopStateContract.COMPLETED
                || workshop.completedAt() == null) {
            return false;
        }
        Instant deadline = workshop.completedAt()
                .plus(Duration.ofMinutes(reconciliationParameters.windowMinutes()));
        return !now.isBefore(deadline);
    }
}
