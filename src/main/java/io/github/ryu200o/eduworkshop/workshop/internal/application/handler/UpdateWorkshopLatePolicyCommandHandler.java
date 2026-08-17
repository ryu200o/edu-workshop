package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopLatePolicyCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Orchestrates the "update the workshop attendance late policy" use case (Epic 3C).
 *
 * <p>Application-layer flow: build the self-validating {@link WorkshopLateThreshold} from the
 * command's seconds value (range 0..86400 — {@link IllegalArgumentException} mapped to HTTP 400)
 * → load the workshop → delegate to the aggregate ({@link Workshop#updateLatePolicy} enforces the
 * lifecycle gate: mutable only until {@code IN_PROGRESS}, per Epic 3C §4) → persist → publish
 * events through the outbox.</p>
 */
@Component
class UpdateWorkshopLatePolicyCommandHandler
        implements CommandHandler<UpdateWorkshopLatePolicyCommand, UpdateWorkshopLatePolicyCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    UpdateWorkshopLatePolicyCommandHandler(WorkshopRepository workshopRepository,
                                           WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                           Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UpdateWorkshopLatePolicyCommand.Result handle(UpdateWorkshopLatePolicyCommand command) {
        Instant now = Instant.now(clock);

        WorkshopLateThreshold lateThreshold = WorkshopLateThreshold.of(command.lateThresholdSeconds());

        Workshop workshop = workshopRepository.loadById(WorkshopId.of(command.workshopId()))
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        workshop.updateLatePolicy(lateThreshold, now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new UpdateWorkshopLatePolicyCommand.Result(workshop.id().value(), lateThreshold.seconds());
    }
}