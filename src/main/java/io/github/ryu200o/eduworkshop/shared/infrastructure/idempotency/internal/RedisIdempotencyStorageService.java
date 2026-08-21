package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal;

import java.time.Duration;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Atomic Redis-backed storage for idempotency reservations (ADR 0022). Uses {@code SET ... NX EX}
 * for the reserve phase and overwrites with JSON metadata on completion. TTL is owned by Redis.
 */
@Service
class RedisIdempotencyStorageService {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisIdempotencyStorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    String buildKey(UUID principalId, String httpMethod, String normalizedPath, String idempotencyKey) {
        return KEY_PREFIX + principalId + ":" + httpMethod + ":" + normalizedPath + ":" + idempotencyKey;
    }

    enum ReserveOutcome {
        RESERVED_NEW,
        EXISTING_IN_PROGRESS,
        EXISTING_COMPLETED
    }

    ReserveOutcome reserve(String key, long ttlSeconds) {
        if (tryReserve(key, ttlSeconds)) {
            return ReserveOutcome.RESERVED_NEW;
        }
        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null) {
            // Edge: IN_PROGRESS expired between setIfAbsent and get (OQ-R10) -> retry once.
            if (tryReserve(key, ttlSeconds)) {
                return ReserveOutcome.RESERVED_NEW;
            }
            existing = redisTemplate.opsForValue().get(key);
        }
        if (existing == null) {
            return ReserveOutcome.RESERVED_NEW;
        }
        if (IN_PROGRESS.equals(existing)) {
            return ReserveOutcome.EXISTING_IN_PROGRESS;
        }
        return ReserveOutcome.EXISTING_COMPLETED;
    }

    private boolean tryReserve(String key, long ttlSeconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, IN_PROGRESS, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    void complete(String key, IdempotencyMetadata metadata, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize idempotency metadata", e);
        }
    }

    IdempotencyMetadata readCompleted(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || IN_PROGRESS.equals(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, IdempotencyMetadata.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize idempotency metadata", e);
        }
    }

    void remove(String key) {
        redisTemplate.delete(key);
    }
}
