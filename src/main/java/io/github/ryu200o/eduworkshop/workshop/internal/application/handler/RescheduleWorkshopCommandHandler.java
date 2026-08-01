package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reschedules a PUBLISHED workshop to a new time window (ADR 0008: post-publish change, room and
 * registrations kept).
 *
 * <p>Orchestration (ADR 0005): load with lock → hard-block if another PUBLISHED workshop occupies
 * the new window in the same room → {@code Workshop.reschedule} (deadline 24h + window validity are
 * local invariants) → evict overlapping PLANNED workshops (now compared against the <em>new</em>
 * window) → save → publish all events via the outbox.</p>
 */
@Component
class RescheduleWorkshopCommandHandler
        implements CommandHandler<RescheduleWorkshopCommand, RescheduleWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final Clock clock;

    RescheduleWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     PlannedWorkshopKicker plannedWorkshopKicker,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.plannedWorkshopKicker = plannedWorkshopKicker;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RescheduleWorkshopCommand.Result handle(RescheduleWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadByIdWithLock(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        UUID roomId = workshop.roomReference().roomId();

        int overlappingPublished = workshopRepository.countOverlapping(
                roomId, command.newStartTime(), command.newEndTime(), workshopId);
        if (overlappingPublished > 0) {
            throw new RoomConflictException(roomId, command.workshopId());
        }

        workshop.reschedule(command.newStartTime(), command.newEndTime(), now);

        List<Workshop> kickedOut = plannedWorkshopKicker.kickOutOverlappingPlanned(roomId, workshop, now);

        workshopRepository.save(workshop);

        List<WorkshopDomainEvent> events = new ArrayList<>(workshop.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        workshop.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);

        return new RescheduleWorkshopCommand.Result(
                workshop.id().value(),
                workshop.startTime(),
                workshop.endTime(),
                workshop.updatedAt());
    }
}
