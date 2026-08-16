package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopCheckInParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopLateThreshold;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Handler for {@link CreateWorkshopCommand}. Validates raw input into domain value objects,
 * computes the Occupancy Window start via the config pure function (ADR 0018: {@code occupancyStart
 * = startTime − currentConfigBuffer}), seeds the attendance late-policy threshold from the Workshop
 * check-in config default (ADR 0019 §13.1, Epic 3C OQ-3C-9: {@code lateAfterMinutes} → seconds),
 * delegates to
 * {@link Workshop#create(WorkshopId, WorkshopTitle, WorkshopDescription, Instant, Instant, Instant, WorkshopCapacity, WorkshopLateThreshold, Instant)},
 * persists via {@link WorkshopRepository}, and returns a lightweight result.
 */
@Component
class CreateWorkshopCommandHandler implements CommandHandler<CreateWorkshopCommand, CreateWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopBufferParameters bufferParameters;
    private final WorkshopCheckInParameters checkInParameters;
    private final Clock clock;

    CreateWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                 WorkshopBufferParameters bufferParameters,
                                 WorkshopCheckInParameters checkInParameters,
                                 Clock clock) {
        this.workshopRepository = workshopRepository;
        this.bufferParameters = bufferParameters;
        this.checkInParameters = checkInParameters;
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

        WorkshopLateThreshold lateThreshold =
                WorkshopLateThreshold.of(checkInParameters.lateAfterMinutes() * 60);

        Workshop workshop = Workshop.create(id, title, description,
                command.startTime(), command.endTime(), occupancyStart, capacity, lateThreshold, now);

        workshopRepository.save(workshop);

        return new CreateWorkshopCommand.Result(id.value(), title.value());
    }
}
