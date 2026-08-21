package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates idempotency framework exceptions into RFC 7807 Problem Details. Global (no
 * {@code assignableTypes}) because the guard applies across all modules (ADR 0022).
 */
@RestControllerAdvice
class IdempotencyExceptionAdvice {

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleMissingKey(MissingIdempotencyKeyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConcurrentIdempotencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail handleConcurrent(ConcurrentIdempotencyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
