package io.github.ryu200o.eduworkshop.room.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.CommandBus;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Driving HTTP adapter exposing the Room write API. Maps REST requests to module commands via the
 * per-module {@link CommandBus}; package-private and confined to the internal boundary.
 */
@RestController
@RequestMapping("/api/v1/rooms")
class RoomController {

    private final CommandBus commandBus;

    RoomController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PutMapping("/{id}/rename")
    ResponseEntity<RoomResponse> rename(@PathVariable UUID id, @RequestBody RenameRoomRequest request) {
        RoomResponse response = commandBus.execute(new RenameRoomCommand(id, request.newCode()));
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(RoomNotFoundException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(DuplicateRoomException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleDuplicate(DuplicateRoomException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(RoomDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String handleDomain(RoomDomainException ex) {
        return ex.getMessage();
    }

    record RenameRoomRequest(String newCode) {
    }
}
