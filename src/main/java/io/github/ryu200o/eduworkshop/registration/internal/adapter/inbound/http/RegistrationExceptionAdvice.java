package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.exception.DuplicateRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.RegistrationNotOwnedByUserException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopCapacityExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.application.exception.WorkshopNotOpenForRegistrationException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.CancellationDeadlineExceededException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.InvalidRegistrationStateException;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception.RegistrationDomainException;
import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error handling for the Registration module's HTTP inbound adapters. Scoped strictly to
 * {@link RegistrationCommandController} and {@link RegistrationQueryController} via
 * {@code assignableTypes} so business-specific translations never leak into the shared kernel —
 * preserving module encapsulation per Spring Modulith.
 */
@RestControllerAdvice(assignableTypes = {RegistrationCommandController.class, RegistrationQueryController.class})
class RegistrationExceptionAdvice {

    @ExceptionHandler(DuplicateRegistrationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleDuplicate(DuplicateRegistrationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(WorkshopNotOpenForRegistrationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleWorkshopNotOpen(WorkshopNotOpenForRegistrationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(WorkshopCapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleCapacityExceeded(WorkshopCapacityExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleInvalidState(InvalidRegistrationStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent modification detected — the registration was changed by another request. Reload and retry.");
    }

    @ExceptionHandler(RegistrationNotOwnedByUserException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ProblemDetail handleNotOwned(RegistrationNotOwnedByUserException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CancellationDeadlineExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleDeadline(CancellationDeadlineExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RegistrationDomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleRegistrationDomain(RegistrationDomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
