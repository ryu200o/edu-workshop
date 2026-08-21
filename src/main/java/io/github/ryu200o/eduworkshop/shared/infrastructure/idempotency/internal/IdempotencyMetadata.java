package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal;

/**
 * Lightweight metadata persisted in Redis for a completed idempotent request.
 * Serialized as JSON {@code {"status":201,"location":"/path"}} (location null for 204).
 */
public record IdempotencyMetadata(int status, String location) {
}
