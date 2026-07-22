package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopPersistenceException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.InvalidWorkshopStateException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error handling for the Workshop module's HTTP driving adapters. Scoped strictly to the
 * Workshop write controller via {@code assignableTypes} so business-specific translations never leak
 * into the shared kernel — preserving module encapsulation per Spring Modulith.
 */
@RestControllerAdvice(assignableTypes = {WorkshopCommandController.class})
class WorkshopExceptionAdvice {

    @ExceptionHandler(InvalidWorkshopStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleInvalidState(InvalidWorkshopStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(WorkshopDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleWorkshopDomain(WorkshopDomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WorkshopPersistenceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ProblemDetail handlePersistence(WorkshopPersistenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
}
