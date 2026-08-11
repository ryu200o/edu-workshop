package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
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
 * computes the Occupancy Window start via the config pure function (ADR 0018: {@code occupancyStart
 * = startTime − currentConfigBuffer}), delegates to
 * {@link Workshop#create(WorkshopId, WorkshopTitle, WorkshopDescription, Instant, Instant, Instant, WorkshopCapacity, Instant)},
 * persists via {@link WorkshopRepository}, and returns a lightweight result.
 */
@Component
class CreateWorkshopCommandHandler implements CommandHandler<CreateWorkshopCommand, CreateWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopBufferParameters bufferParameters;
    private final Clock clock;

    CreateWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                 WorkshopBufferParameters bufferParameters,
                                 Clock clock) {
        this.workshopRepository = workshopRepository;
        this.bufferParameters = bufferParameters;
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

        Instant occupancyStart = command.startTime()
                .minus(java.time.Duration.ofMinutes(bufferParameters.beforeDefaultMinutes()));

        Workshop workshop = Workshop.create(id, title, description,
                command.startTime(), command.endTime(), occupancyStart, capacity, now);

        workshopRepository.save(workshop);

        return new CreateWorkshopCommand.Result(id.value(), title.value());
    }
}
