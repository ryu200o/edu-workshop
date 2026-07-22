package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {WorkshopCommandController.class})
class WorkshopExceptionAdvice {

    @ExceptionHandler(WorkshopDomainException.class)
    ProblemDetail handleWorkshopDomain(WorkshopDomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
