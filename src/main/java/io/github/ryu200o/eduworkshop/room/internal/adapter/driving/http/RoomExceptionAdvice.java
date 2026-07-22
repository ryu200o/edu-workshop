package io.github.ryu200o.eduworkshop.room.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomPersistenceException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomCodeException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.DuplicateRoomNameException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.IllegalRoomStateException;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error handling for the Room module's HTTP driving adapters. Scoped strictly to the Room
 * write/read controllers via {@code assignableTypes} so the business-specific translations never leak
 * into the shared kernel — preserving module encapsulation per Spring Modulith.
 */
@RestControllerAdvice(assignableTypes = {RoomCommandController.class, RoomQueryController.class})
class RoomExceptionAdvice {

    @ExceptionHandler(RoomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(RoomNotFoundException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(DuplicateRoomCodeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleDuplicateCode(DuplicateRoomCodeException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(DuplicateRoomNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleDuplicateName(DuplicateRoomNameException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(IllegalRoomStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleIllegalState(IllegalRoomStateException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String handleIllegalArgument(IllegalArgumentException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(RoomDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String handleDomain(RoomDomainException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(RoomPersistenceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    String handlePersistence(RoomPersistenceException ex) {
        return ex.getMessage();
    }
}
