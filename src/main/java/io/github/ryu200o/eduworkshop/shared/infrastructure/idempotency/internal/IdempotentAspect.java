package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal.exception.ConcurrentIdempotencyException;
import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal.exception.MissingIdempotencyKeyException;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

/**
 * Ingress idempotency guard (ADR 0022). Intercepts every {@code @Idempotent} controller
 * method: reserves a Redis key, executes once, replays on duplicate keys, and removes the key on
 * failure so clients can retry safely.
 */
@Aspect
@Order(1)
@Component
class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final UUID NIL_PRINCIPAL = new UUID(0L, 0L);
    private static final int MAX_KEY_LENGTH = 64;

    private final RedisIdempotencyStorageService storageService;

    IdempotentAspect(RedisIdempotencyStorageService storageService) {
        this.storageService = storageService;
    }

    @Around("@annotation(cmd)")
    public Object guard(ProceedingJoinPoint pjp, Idempotent cmd) throws Throwable {
        HttpServletRequest request = currentRequest();
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new MissingIdempotencyKeyException();
        }

        UUID principalId = resolvePrincipalId();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String key = storageService.buildKey(principalId, method, path, idempotencyKey);
        long ttlSeconds = cmd.ttlMinutes() * 60;

        return switch (storageService.reserve(key, ttlSeconds)) {
            case RESERVED_NEW -> executeAndFinalize(pjp, key, ttlSeconds);
            case EXISTING_IN_PROGRESS -> throw new ConcurrentIdempotencyException();
            case EXISTING_COMPLETED -> replayOrExecute(pjp, key, ttlSeconds);
        };
    }

    private Object executeAndFinalize(ProceedingJoinPoint pjp, String key, long ttlSeconds) throws Throwable {
        try {
            Object result = pjp.proceed();
            int status;
            String location = null;
            if (result instanceof ResponseEntity<?> response) {
                status = response.getStatusCode().value();
                URI uri = response.getHeaders().getLocation();
                if (uri != null) {
                    location = uri.toString();
                }
            } else {
                status = 200;
            }
            storageService.complete(key, new IdempotencyMetadata(status, location), ttlSeconds);
            return result;
        } catch (Throwable t) {
            storageService.remove(key);
            log.debug("Removed idempotency key after failure: {}", key);
            throw t;
        }
    }

    private Object replayOrExecute(ProceedingJoinPoint pjp, String key, long ttlSeconds) throws Throwable {
        IdempotencyMetadata metadata = storageService.readCompleted(key);
        if (metadata == null) {
            // Completed entry expired in the tiny window between reserve-read and replay-read:
            // safely re-execute once (the business handler remains the authority on duplicates).
            log.debug("Completed idempotency entry missing on replay (TTL race); re-executing: {}", key);
            return executeAndFinalize(pjp, key, ttlSeconds);
        }
        if (metadata.location() != null) {
            return ResponseEntity.status(metadata.status())
                    .location(URI.create(metadata.location()))
                    .build();
        }
        return ResponseEntity.status(metadata.status()).build();
    }

    private UUID resolvePrincipalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return NIL_PRINCIPAL;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}
