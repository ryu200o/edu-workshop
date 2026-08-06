package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CatchUpWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Handler for the stale catch-up (Epic 1, D3). Loads a {@code PUBLISHED} workshop that is overdue
 * and rushes it through {@code Workshop.start(now)} then {@code Workshop.complete(now)} within ONE
 * transaction: the single {@code save} and the publication of BOTH {@code WorkshopStarted} and
 * {@code WorkshopCompleted} share the same transaction scope (outbox, ADR 0011). On any failure the
 * whole transition rolls back atomically — a workshop can never be left stuck in {@code IN_PROGRESS}
 * past its end time.
 *
 * <p>{@code @Transactional} on {@code handle()} works here because the shared CommandBus resolves
 * this handler through the Spring proxy (unlike a self-call), keeping the D3 atomicity guarantee.</p>
 */
@Component
class CatchUpWorkshopCommandHandler
        implements CommandHandler<CatchUpWorkshopCommand, CatchUpWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    CatchUpWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                  WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                  Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CatchUpWorkshopCommand.Result handle(CatchUpWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        workshop.start(now);
        workshop.complete(now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new CatchUpWorkshopCommand.Result(workshop.id().value(), now, workshop.state().name());
    }
}
