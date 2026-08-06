package io.github.ryu200o.eduworkshop.workshop.internal.application.scheduler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Performs the stale catch-up for a single overdue workshop inside ONE transaction (Epic 1, D3).
 *
 * <p>Kept as a separate {@code @Service} (not a method on the scanner) on purpose: Spring's
 * {@code @Transactional} only applies when the call crosses a proxied bean boundary — a self-call
 * from the scanner's own {@code scan()} would bypass the proxy and silently split the transition
 * into no transaction at all. By routing through this bean, {@code start()} + {@code complete()} +
 * {@code save} + {@code publish} are guaranteed to share a single transaction scope: either all
 * succeed and both {@code WorkshopStarted} and {@code WorkshopCompleted} are published (outbox,
 * ADR 0011), or none are applied — a workshop can never be left stuck in {@code IN_PROGRESS} past
 * its end time.</p>
 */
@Service
class WorkshopCatchUpService {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;

    WorkshopCatchUpService(WorkshopRepository workshopRepository,
                           WorkshopDomainEventPublisher workshopDomainEventPublisher) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
    }

    @Transactional
    void catchUp(UUID id, Instant now) {
        Workshop workshop = workshopRepository.loadById(WorkshopId.of(id))
                .orElseThrow(() -> new WorkshopNotFoundException("id", id));
        workshop.start(now);
        workshop.complete(now);
        workshopRepository.save(workshop);
        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();
    }
}
