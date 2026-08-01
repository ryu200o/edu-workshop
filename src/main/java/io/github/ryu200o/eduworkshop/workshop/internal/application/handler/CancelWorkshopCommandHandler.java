package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CancelWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class CancelWorkshopCommandHandler
        implements CommandHandler<CancelWorkshopCommand, CancelWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    CancelWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                 WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                 Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CancelWorkshopCommand.Result handle(CancelWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadByIdWithLock(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        workshop.cancel(now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new CancelWorkshopCommand.Result(workshop.id().value(), workshop.updatedAt());
    }
}
