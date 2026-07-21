package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopCapacity;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopDescription;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopTitle;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class CreateWorkshopCommandHandler implements CommandHandler<CreateWorkshopCommand, CreateWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final Clock clock;

    CreateWorkshopCommandHandler(WorkshopRepository workshopRepository, Clock clock) {
        this.workshopRepository = workshopRepository;
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

        Workshop workshop = Workshop.create(id, title, description,
                command.startTime(), command.endTime(), capacity, now);

        workshopRepository.save(workshop);

        return new CreateWorkshopCommand.Result(id.value(), title.value());
    }
}
