package io.github.ryu200o.eduworkshop.room.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.DeactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.PlaceRoomUnderMaintenanceCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ReactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ScheduleRoomMaintenanceCommand;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Room WRITE side (Command). Accepts only state-changing HTTP methods
 * (POST, PUT, DELETE) and talks exclusively to the shared {@link CommandBus}. Package-private
 * and confined to the module's internal boundary. Error handling is centralized in {@link
 * RoomExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/rooms")
class RoomCommandController {

    private final CommandBus commandBus;

    RoomCommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Idempotent
    @PostMapping
    ResponseEntity<Void> create(@RequestBody CreateRoomRequest request) {
        UUID roomId = UUID.randomUUID();
        var command = new CreateRoomCommand(roomId, request.building(), request.floor(), request.code(), request.name(), request.capacity());
        commandBus.execute(command);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + roomId)).build();
    }

    @PutMapping("/{id}/rename")
    ResponseEntity<Void> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newName());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/code")
    ResponseEntity<Void> changeCode(@PathVariable UUID id, @RequestBody ChangeRoomCodeRequest request) {
        var command = new ChangeRoomCodeCommand(id, request.newCode());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/relocate")
    ResponseEntity<Void> relocate(@PathVariable UUID id, @RequestBody RelocateRoomRequest request) {
        var command = new RelocateRoomCommand(id, request.newBuilding(), request.newFloor());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/capacity")
    ResponseEntity<Void> changeCapacity(@PathVariable UUID id, @RequestBody ChangeRoomCapacityRequest request) {
        var command = new ChangeRoomCapacityCommand(id, request.newCapacity());
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/maintenance")
    ResponseEntity<Void> placeUnderMaintenance(@PathVariable UUID id) {
        var command = new PlaceRoomUnderMaintenanceCommand(id);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        var command = new ReactivateRoomCommand(id);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        var command = new DeactivateRoomCommand(id);
        commandBus.execute(command);
        return ResponseEntity.noContent().build();
    }

    @Idempotent
    @PostMapping("/{id}/maintenance-schedules")
    ResponseEntity<Void> scheduleMaintenance(
            @PathVariable UUID id, @RequestBody ScheduleMaintenanceRequest request) {
        UUID maintenanceId = UUID.randomUUID();
        var command = new ScheduleRoomMaintenanceCommand(
                id, request.startTime(), request.endTime(), request.reason(), request.operator(), maintenanceId);
        commandBus.execute(command);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + id + "/maintenance-schedules/" + maintenanceId)).build();
    }

    record CreateRoomRequest(String building, int floor, int code, String name, int capacity) {
    }

    record RenameRoomRequest(String newName) {
    }

    record ChangeRoomCodeRequest(int newCode) {
    }

    record RelocateRoomRequest(String newBuilding, int newFloor) {
    }

    record ChangeRoomCapacityRequest(int newCapacity) {
    }

    record ScheduleMaintenanceRequest(Instant startTime, Instant endTime, String reason, String operator) {
    }
}
