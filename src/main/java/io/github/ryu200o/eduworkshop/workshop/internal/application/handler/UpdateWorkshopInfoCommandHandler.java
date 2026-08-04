package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.contract.CountActiveRegistrationsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopInfoCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Updates the title and/or description of a workshop.
 *
 * <p>Orchestration (ADR 0005): load with lock → fetch activeRegistrations via QueryBus
 * (ADR 0006) → {@code Workshop.updateInformation} (state guard + title-lock invariant) →
 * save → publish domain events via outbox.</p>
 */
@Component
class UpdateWorkshopInfoCommandHandler
        implements CommandHandler<UpdateWorkshopInfoCommand, UpdateWorkshopInfoCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final QueryBus queryBus;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    UpdateWorkshopInfoCommandHandler(WorkshopRepository workshopRepository,
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
    public UpdateWorkshopInfoCommand.Result handle(UpdateWorkshopInfoCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        int activeRegistrations = queryBus.execute(
                new CountActiveRegistrationsQuery(command.workshopId()));

        workshop.updateInformation(
                WorkshopTitle.of(command.newTitle()),
                WorkshopDescription.of(command.newDescription()),
                activeRegistrations,
                now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new UpdateWorkshopInfoCommand.Result(
                workshop.id().value(),
                workshop.title().value(),
                workshop.description().value(),
                workshop.updatedAt());
    }
}