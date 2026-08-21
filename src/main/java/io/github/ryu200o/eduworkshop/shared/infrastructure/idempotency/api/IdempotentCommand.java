package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an HTTP inbound Command method must be guarded by the idempotency framework.
 * Callers must send an {@code Idempotency-Key} header (1-64 chars); the framework reserves a
 * Redis key, executes the business logic once, and replays the stored result on subsequent
 * identical requests (ADR 0022).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentCommand {
    long ttlMinutes() default 1440;
}
