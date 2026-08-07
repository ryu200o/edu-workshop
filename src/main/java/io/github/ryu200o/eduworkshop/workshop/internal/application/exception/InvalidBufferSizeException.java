package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Thrown when a requested buffer time lies outside the Operational Policy bounds
 * ({@code app.workshop.buffer.min-maxutes} / {@code max-minutes}). Application-layer concern — the domain
 * {@link io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer} only enforces the
 * non-negative local invariant (ADR 0018 P2).
 */
public class InvalidBufferSizeException extends ApplicationException {

    public InvalidBufferSizeException(String message) {
        super(message);
    }
}
