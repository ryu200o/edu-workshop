package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.CreateWorkshopCommand;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

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

    record CreateWorkshopRequest(
            String title,
            String description,
            Instant startTime,
            Instant endTime,
            int capacity
    ) {
    }
}
