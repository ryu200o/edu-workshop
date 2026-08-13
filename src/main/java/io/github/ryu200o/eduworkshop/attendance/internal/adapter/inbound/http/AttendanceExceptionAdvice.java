package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.AttendanceRoleViolationException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.RegistrationNotVerifiedException;
import io.github.ryu200o.eduworkshop.attendance.internal.application.exception.WorkshopNotInSessionException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.AttendanceDomainException;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception.ReconciliationWindowExceededException;
import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error handling for the Attendance module's HTTP inbound adapters. Scoped strictly to
 * the two controllers via {@code assignableTypes} so business-specific translations never leak into
 * the shared kernel — preserving module encapsulation per Spring Modulith.
 */
@RestControllerAdvice(assignableTypes = {AttendanceCommandController.class, AttendanceQueryController.class})
class AttendanceExceptionAdvice {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RegistrationNotVerifiedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleNotVerified(RegistrationNotVerifiedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(WorkshopNotInSessionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleNotInSession(WorkshopNotInSessionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ReconciliationWindowExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleWindowExceeded(ReconciliationWindowExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AttendanceDomainException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleDomainConflict(AttendanceDomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent modification detected — the attendance record was changed by another request. Reload and retry.");
    }

    @ExceptionHandler(AttendanceRoleViolationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ProblemDetail handleRoleViolation(AttendanceRoleViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}