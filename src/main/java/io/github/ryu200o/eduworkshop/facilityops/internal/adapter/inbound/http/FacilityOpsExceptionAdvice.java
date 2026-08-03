package io.github.ryu200o.eduworkshop.facilityops.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.facilityops.internal.application.exception.FacilityRoomNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error handling for the FacilityOps module's HTTP inbound adapters. Scoped strictly to
 * the FacilityOps controllers via {@code assignableTypes} so the translations never leak into other
 * modules — preserving module encapsulation per Spring Modulith. Errors are returned as RFC 9457
 * {@link ProblemDetail} bodies ({@code application/problem+json}).
 */
@RestControllerAdvice(assignableTypes = FacilityOpsQueryController.class)
class FacilityOpsExceptionAdvice {

    @ExceptionHandler(FacilityRoomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleNotFound(FacilityRoomNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
