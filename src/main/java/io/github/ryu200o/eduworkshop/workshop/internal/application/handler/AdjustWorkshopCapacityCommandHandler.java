package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.contract.CountActiveRegistrationsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.AdjustWorkshopCapacityCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.AdjustmentJustification;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class AdjustWorkshopCapacityCommandHandler
        implements CommandHandler<AdjustWorkshopCapacityCommand, AdjustWorkshopCapacityCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final QueryBus queryBus;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    AdjustWorkshopCapacityCommandHandler(WorkshopRepository workshopRepository,
                                         QueryBus queryBus,
                                         WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                         Clock clock) {
        this.workshopRepository = workshopRepository;
        this.queryBus = queryBus;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdjustWorkshopCapacityCommand.Result handle(AdjustWorkshopCapacityCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());
        AdjustmentJustification justification = AdjustmentJustification.of(command.justification());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        int activeRegistrations = queryBus.execute(new CountActiveRegistrationsQuery(command.workshopId()));

        WorkshopCapacity newCapacity = WorkshopCapacity.of(command.newCapacity());
        workshop.adjustCapacity(newCapacity, activeRegistrations, now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new AdjustWorkshopCapacityCommand.Result(
                workshop.id().value(), workshop.capacity().value(), workshop.updatedAt());
    }
}
