package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.AdjustWorkshopCapacityCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CancelWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.ChangeWorkshopRoomCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CompleteWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PlanWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PublishWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.StartWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UnplanWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopInfoCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopLatePolicyCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.UpdateWorkshopScheduleCommand;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;

import org.springframework.http.ResponseEntity;
import java.net.URI;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @Idempotent
    @PostMapping
    ResponseEntity<Void> create(@RequestBody CreateWorkshopRequest request) {
        UUID workshopId = UUID.randomUUID();
        var command = new CreateWorkshopCommand(
                workshopId,
                request.title(),
                request.description(),
                request.startTime(),
                request.endTime(),
                request.capacity());
        commandBus.execute(command);
        return ResponseEntity.created(URI.create("/api/v1/workshops/" + workshopId)).build();
    }

    @PostMapping("/{id}/plan")
    ResponseEntity<Void> plan(@PathVariable UUID id,
                              @RequestBody PlanWorkshopRequest request) {
        commandBus.execute(new PlanWorkshopCommand(id, request.roomId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    ResponseEntity<Void> publish(@PathVariable UUID id) {
        commandBus.execute(new PublishWorkshopCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        commandBus.execute(new CancelWorkshopCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    ResponseEntity<Void> start(@PathVariable UUID id) {
        commandBus.execute(new StartWorkshopCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<Void> complete(@PathVariable UUID id) {
        commandBus.execute(new CompleteWorkshopCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/change-room")
    ResponseEntity<Void> changeRoom(@PathVariable UUID id,
                                    @RequestBody ChangeWorkshopRoomRequest request) {
        commandBus.execute(new ChangeWorkshopRoomCommand(id, request.roomId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/adjust-capacity")
    ResponseEntity<Void> adjustCapacity(@PathVariable UUID id,
                                        @RequestBody AdjustWorkshopCapacityRequest request) {
        commandBus.execute(new AdjustWorkshopCapacityCommand(id, request.newCapacity()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reschedule")
    ResponseEntity<Void> reschedule(@PathVariable UUID id,
                                    @RequestBody RescheduleWorkshopRequest request) {
        commandBus.execute(new RescheduleWorkshopCommand(id, request.newStartTime(), request.newEndTime()));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/info")
    ResponseEntity<Void> updateInfo(@PathVariable UUID id,
                                    @RequestBody UpdateWorkshopInfoRequest request) {
        commandBus.execute(new UpdateWorkshopInfoCommand(id, request.newTitle(), request.newDescription()));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/schedule")
    ResponseEntity<Void> updateSchedule(@PathVariable UUID id,
                                        @RequestBody UpdateWorkshopScheduleRequest request) {
        commandBus.execute(new UpdateWorkshopScheduleCommand(id, request.newStartTime(), request.newEndTime()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/late-policy")
    ResponseEntity<Void> updateLatePolicy(@PathVariable UUID id,
                                          @RequestBody UpdateWorkshopLatePolicyRequest request) {
        commandBus.execute(new UpdateWorkshopLatePolicyCommand(id, request.lateThresholdSeconds()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/plan")
    ResponseEntity<Void> unplan(@PathVariable UUID id) {
        commandBus.execute(new UnplanWorkshopCommand(id));
        return ResponseEntity.noContent().build();
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

    record UpdateWorkshopInfoRequest(String newTitle, String newDescription) {
    }

    record UpdateWorkshopScheduleRequest(Instant newStartTime, Instant newEndTime) {
    }

    record UpdateWorkshopLatePolicyRequest(int lateThresholdSeconds) {
    }
}
