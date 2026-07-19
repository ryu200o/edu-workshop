package io.github.ryu200o.eduworkshop.room.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCapacityCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ChangeRoomCodeCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.DeactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.PlaceRoomUnderMaintenanceCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.ReactivateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RelocateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    ResponseEntity<CreateRoomCommand.Result> create(@RequestBody CreateRoomRequest request) {
        var command = new CreateRoomCommand(request.building(), request.floor(), request.code(), request.name(), request.capacity());
        CreateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/rename")
    ResponseEntity<RenameRoomCommand.Result> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newName());
        RenameRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/code")
    ResponseEntity<ChangeRoomCodeCommand.Result> changeCode(@PathVariable UUID id, @RequestBody ChangeRoomCodeRequest request) {
        var command = new ChangeRoomCodeCommand(id, request.newCode());
        ChangeRoomCodeCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/relocate")
    ResponseEntity<RelocateRoomCommand.Result> relocate(@PathVariable UUID id, @RequestBody RelocateRoomRequest request) {
        var command = new RelocateRoomCommand(id, request.newBuilding(), request.newFloor());
        RelocateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/capacity")
    ResponseEntity<ChangeRoomCapacityCommand.Result> changeCapacity(@PathVariable UUID id, @RequestBody ChangeRoomCapacityRequest request) {
        var command = new ChangeRoomCapacityCommand(id, request.newCapacity());
        ChangeRoomCapacityCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/maintenance")
    ResponseEntity<PlaceRoomUnderMaintenanceCommand.Result> placeUnderMaintenance(@PathVariable UUID id) {
        var command = new PlaceRoomUnderMaintenanceCommand(id);
        PlaceRoomUnderMaintenanceCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<ReactivateRoomCommand.Result> reactivate(@PathVariable UUID id) {
        var command = new ReactivateRoomCommand(id);
        ReactivateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<DeactivateRoomCommand.Result> deactivate(@PathVariable UUID id) {
        var command = new DeactivateRoomCommand(id);
        DeactivateRoomCommand.Result result = commandBus.execute(command);
        return ResponseEntity.ok(result);
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
}
