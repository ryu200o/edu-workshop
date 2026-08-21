package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal.exception;

/**
 * Thrown when an {@code @IdempotentCommand} endpoint is called without a valid
 * {@code Idempotency-Key} header (missing, blank, or longer than 64 chars). Maps to HTTP 400.
 */
public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException() {
        super("Idempotency-Key header is required (1-64 characters).");
    }
}
