package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.AdjustWorkshopCapacityCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CancelWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.ChangeWorkshopRoomCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PlanWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PublishWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UnplanWorkshopCommand;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Workshop WRITE side (Command). Accepts only state-changing HTTP methods
 * (POST / DELETE) and talks exclusively to the shared {@link CommandBus}. Package-private and confined
 * to the module's internal boundary. Error handling is centralized in {@link WorkshopExceptionAdvice}.
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

    @PostMapping("/{id}/plan")
    ResponseEntity<PlanWorkshopCommand.Result> plan(@PathVariable UUID id,
                                                    @RequestBody PlanWorkshopRequest request) {
        var command = new PlanWorkshopCommand(id, request.roomId());
        PlanWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/publish")
    ResponseEntity<PublishWorkshopCommand.Result> publish(@PathVariable UUID id) {
        var command = new PublishWorkshopCommand(id);
        PublishWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<CancelWorkshopCommand.Result> cancel(@PathVariable UUID id) {
        var command = new CancelWorkshopCommand(id);
        CancelWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/change-room")
    ResponseEntity<ChangeWorkshopRoomCommand.Result> changeRoom(@PathVariable UUID id,
                                                                @RequestBody ChangeWorkshopRoomRequest request) {
        var command = new ChangeWorkshopRoomCommand(id, request.roomId());
        ChangeWorkshopRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/adjust-capacity")
    ResponseEntity<AdjustWorkshopCapacityCommand.Result> adjustCapacity(@PathVariable UUID id,
                                                                        @RequestBody AdjustWorkshopCapacityRequest request) {
        var command = new AdjustWorkshopCapacityCommand(id, request.newCapacity());
        AdjustWorkshopCapacityCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reschedule")
    ResponseEntity<RescheduleWorkshopCommand.Result> reschedule(@PathVariable UUID id,
                                                                @RequestBody RescheduleWorkshopRequest request) {
        var command = new RescheduleWorkshopCommand(id, request.newStartTime(), request.newEndTime());
        RescheduleWorkshopCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}/plan")
    ResponseEntity<UnplanWorkshopCommand.Result> unplan(@PathVariable UUID id) {
        UnplanWorkshopCommand.Result result = commandBus.execute(new UnplanWorkshopCommand(id));
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

    record PlanWorkshopRequest(UUID roomId) {
    }

    record ChangeWorkshopRoomRequest(UUID roomId) {
    }

    record AdjustWorkshopCapacityRequest(int newCapacity) {
    }

    record RescheduleWorkshopRequest(Instant newStartTime, Instant newEndTime) {
    }
}
