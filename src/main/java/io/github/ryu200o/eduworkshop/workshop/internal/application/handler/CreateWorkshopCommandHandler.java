package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.config.WorkshopBufferConfig;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.InvalidBufferSizeException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Handler for {@link CreateWorkshopCommand}. Validates raw input into domain value objects,
 * delegates to {@link Workshop#create}, persists via {@link WorkshopRepository}, and returns a
 * lightweight result. Buffer values are resolved against {@link WorkshopBufferConfig} (Operational
 * Policy — ADR 0018 P2) and validated against the configured min/max bounds.
 */
@Component
class CreateWorkshopCommandHandler implements CommandHandler<CreateWorkshopCommand, CreateWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopBufferConfig workshopBufferConfig;
    private final Clock clock;

    CreateWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                 WorkshopBufferConfig workshopBufferConfig,
                                 Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopBufferConfig = workshopBufferConfig;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreateWorkshopCommand.Result handle(CreateWorkshopCommand command) {
        Instant now = Instant.now(clock);

        WorkshopId id = WorkshopId.generate();
        WorkshopTitle title = WorkshopTitle.of(command.title());
        WorkshopDescription description = WorkshopDescription.of(command.description());
        WorkshopCapacity capacity = WorkshopCapacity.of(command.capacity());
        WorkshopBuffer buffer = resolveBuffer(command.bufferBeforeMinutes(), command.bufferAfterMinutes());

        Workshop workshop = Workshop.create(id, title, description,
                command.startTime(), command.endTime(), buffer, capacity, now);

        workshopRepository.save(workshop);

        return new CreateWorkshopCommand.Result(id.value(), title.value());
    }

    private WorkshopBuffer resolveBuffer(Integer before, Integer after) {
        int resolvedBefore = before != null ? before : workshopBufferConfig.beforeDefaultMinutes();
        int resolvedAfter = after != null ? after : workshopBufferConfig.afterDefaultMinutes();

        if (resolvedBefore < workshopBufferConfig.minMinutes() || resolvedBefore > workshopBufferConfig.maxMinutes()
                || resolvedAfter < workshopBufferConfig.minMinutes() || resolvedAfter > workshopBufferConfig.maxMinutes()) {
            throw new InvalidBufferSizeException(
                    "buffer before/after must be within [" + workshopBufferConfig.minMinutes()
                            + ", " + workshopBufferConfig.maxMinutes() + "] minutes");
        }
        return WorkshopBuffer.of(resolvedBefore, resolvedAfter);
    }
}
