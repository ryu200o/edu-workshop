package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal.exception;

/**
 * Thrown when a duplicate {@code Idempotency-Key} is seen while the previous request is still
 * in progress. Maps to HTTP 409.
 */
public class ConcurrentIdempotencyException extends RuntimeException {
    public ConcurrentIdempotencyException() {
        super("A request with the same Idempotency-Key is already in progress.");
    }
}
