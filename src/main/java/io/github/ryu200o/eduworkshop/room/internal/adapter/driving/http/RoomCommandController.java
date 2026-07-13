package io.github.ryu200o.eduworkshop.room.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CommandBus;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RoomCreatedResult;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RoomRenamedResult;
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
 * (POST, PUT, DELETE) and talks exclusively to the module's internal {@link CommandBus}. Package-private
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
    ResponseEntity<RoomCreatedResult> create(@RequestBody CreateRoomRequest request) {
        var command = new CreateRoomCommand(request.building(), request.floor(), request.capacity(), request.roomCode());
        RoomCreatedResult result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/rename")
    ResponseEntity<RoomRenamedResult> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        var command = new RenameRoomCommand(id, request.newCode());
        RoomRenamedResult result = commandBus.execute(command);
        return ResponseEntity.ok(result);
    }

    record CreateRoomRequest(String building, int floor, int capacity, String roomCode) {
    }

    record RenameRoomRequest(String newCode) {
    }
}
