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
 * <p>Application-layer flow: parse the {@code "mm:ss"} input into seconds (chốt OQ-3C-8 — invalid
 * format/range → {@link IllegalArgumentException} mapped to HTTP 400) → build the self-validating
 * {@link WorkshopLateThreshold} (range 0..86400, OQ-3C-7) → load the workshop → delegate to the
 * aggregate ({@link Workshop#updateLatePolicy} enforces the lifecycle gate: mutable only until
 * {@code IN_PROGRESS}, per Epic 3C §4) → persist → publish events through the outbox.</p>
 */
@Component
class UpdateWorkshopLatePolicyCommandHandler
        implements CommandHandler<UpdateWorkshopLatePolicyCommand, UpdateWorkshopLatePolicyCommand.Result> {

    private static final int SECONDS_PER_MINUTE = 60;

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

        WorkshopLateThreshold lateThreshold = WorkshopLateThreshold.of(parseToSeconds(command.lateThreshold()));

        Workshop workshop = workshopRepository.loadById(WorkshopId.of(command.workshopId()))
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        workshop.updateLatePolicy(lateThreshold, now);

        workshopRepository.save(workshop);

        workshopDomainEventPublisher.publish(workshop.recordedEvents());
        workshop.clearDomainEvents();

        return new UpdateWorkshopLatePolicyCommand.Result(workshop.id().value(), lateThreshold.seconds());
    }

    /**
     * Normalizes a {@code "mm:ss"} (or bare {@code "mm"}) input into seconds (chốt OQ-3C-8). Rejects
     * malformed input, {@code ss >= 60}, and negative minutes with {@link IllegalArgumentException}.
     */
    static int parseToSeconds(String lateThreshold) {
        if (lateThreshold == null) {
            throw new IllegalArgumentException("lateThreshold must not be blank.");
        }
        String normalized = lateThreshold.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("lateThreshold must not be blank.");
        }

        String minutesPart;
        String secondsPart = "0";
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("lateThreshold must use mm:ss format.");
            }
            minutesPart = parts[0].trim();
            secondsPart = parts[1].trim();
        } else {
            minutesPart = normalized;
        }

        if (minutesPart.isEmpty()) {
            throw new IllegalArgumentException("lateThreshold minutes must not be blank.");
        }

        int minutes;
        try {
            minutes = Integer.parseInt(minutesPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("lateThreshold minutes must be an integer.", ex);
        }
        if (minutes < 0) {
            throw new IllegalArgumentException("lateThreshold must not be negative.");
        }

        int seconds;
        try {
            seconds = Integer.parseInt(secondsPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("lateThreshold seconds must be an integer.", ex);
        }
        if (seconds >= SECONDS_PER_MINUTE) {
            throw new IllegalArgumentException("lateThreshold seconds must be below 60.");
        }

        return minutes * SECONDS_PER_MINUTE + seconds;
    }
}