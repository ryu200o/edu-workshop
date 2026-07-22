package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.ScheduleWorkshopCommand;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Workshop WRITE side (Command). Accepts only state-changing HTTP methods
 * (POST) and talks exclusively to the shared {@link CommandBus}. Package-private and confined to the
 * module's internal boundary. Error handling is centralized in {@link WorkshopExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/workshops")
class WorkshopCommandController {

    private final CommandBus commandBus;

    WorkshopCommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping
    ResponseEntity<CreateWorkshopCommand.Result> create(@RequestBody CreateWorkshopRequest request) {
        var command = new CreateWorkshopCommand(
                request.title(),
                request.description(),
                request.startTime(),
                request.endTime(),
                request.capacity());
        CreateWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/schedule")
    ResponseEntity<ScheduleWorkshopCommand.Result> schedule(@PathVariable UUID id,
                                                            @RequestBody ScheduleWorkshopRequest request) {
        var command = new ScheduleWorkshopCommand(id, request.roomId());
        ScheduleWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    record CreateWorkshopRequest(
            String title,
            String description,
            Instant startTime,
            Instant endTime,
            int capacity
    ) {
    }

    record ScheduleWorkshopRequest(UUID roomId) {
    }
}
