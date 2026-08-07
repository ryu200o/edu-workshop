package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.InvalidBufferSizeException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Handler for {@link CreateWorkshopCommand}. Validates raw input into domain value objects,
 * delegates to {@link Workshop#create}, persists via {@link WorkshopRepository}, and returns a
 * lightweight result. Buffer values are resolved against the Operational Policy
 * ({@code app.workshop.buffer.*}) and validated against the configured min/max bounds (ADR 0018 P2).
 */
@Component
class CreateWorkshopCommandHandler implements CommandHandler<CreateWorkshopCommand, CreateWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final Clock clock;
    private final int beforeDefaultMinutes;
    private final int afterDefaultMinutes;
    private final int minMinutes;
    private final int maxMinutes;

    CreateWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                 Clock clock,
                                 @Value("${app.workshop.buffer.before-default-minutes:15}") int beforeDefaultMinutes,
                                 @Value("${app.workshop.buffer.after-default-minutes:15}") int afterDefaultMinutes,
                                 @Value("${app.workshop.buffer.min-minutes:0}") int minMinutes,
                                 @Value("${app.workshop.buffer.max-minutes:60}") int maxMinutes) {
        this.workshopRepository = workshopRepository;
        this.clock = clock;
        this.beforeDefaultMinutes = beforeDefaultMinutes;
        this.afterDefaultMinutes = afterDefaultMinutes;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
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
        int resolvedBefore = before != null ? before : beforeDefaultMinutes;
        int resolvedAfter = after != null ? after : afterDefaultMinutes;

        if (resolvedBefore < minMinutes || resolvedBefore > maxMinutes
                || resolvedAfter < minMinutes || resolvedAfter > maxMinutes) {
            throw new InvalidBufferSizeException(
                    "buffer before/after must be within [" + minMinutes + ", " + maxMinutes + "] minutes");
        }
        return WorkshopBuffer.of(resolvedBefore, resolvedAfter);
    }
}
