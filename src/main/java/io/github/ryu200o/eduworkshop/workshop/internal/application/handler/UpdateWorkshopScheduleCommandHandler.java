package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopScheduleCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Updates the time window of a pre-publish workshop (DRAFT or PLANNED only).
 *
 * <p>Orchestration (ADR 0005): load → compute the new Occupancy Window start via the config pure
 * function (ADR 0018: {@code occupancyStart = newStartTime − currentConfigBuffer}) → {@code
 * Workshop.updateSchedule} (state guard + time-window validity) → save → publish domain events via
 * outbox.</p>
 */
@Component
class UpdateWorkshopScheduleCommandHandler
        implements CommandHandler<UpdateWorkshopScheduleCommand> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final WorkshopBufferParameters bufferParameters;
    private final Clock clock;

    UpdateWorkshopScheduleCommandHandler(WorkshopRepository workshopRepository,
                                         WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                         WorkshopBufferParameters bufferParameters,
                                         Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.bufferParameters = bufferParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(UpdateWorkshopScheduleCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        Instant newOccupancyStart = command.newStartTime()
                .minus(Duration.ofMinutes(bufferParameters.beforeDefaultMinutes()));

        workshop.updateSchedule(command.newStartTime(), command.newEndTime(), newOccupancyStart, now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();
    }
}