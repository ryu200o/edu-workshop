package io.github.ryu200o.eduworkshop.shared.test;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.TestSocketUtils;

import redis.embedded.RedisServer;

/**
 * Global test {@link Configuration} that starts a single embedded Redis server on a random free TCP
 * port for the whole test JVM and exposes a {@link RedisConnectionFactory} bound to it. Because it is
 * on the test classpath and component-scanned by every {@code @SpringBootTest} context, the
 * idempotency-guarded endpoints (and any other Redis-touching code) can talk to Redis without Docker
 * (CI has no Docker; OQ-R2). Boot's own {@code RedisAutoConfiguration} backs off because a
 * {@code RedisConnectionFactory} bean is already present.
 */
@Configuration
public class EmbeddedRedisConfiguration {

    private static final RedisServer REDIS_SERVER;
    private static final int REDIS_PORT;

    static {
        try {
            REDIS_PORT = TestSocketUtils.findAvailableTcpPort();
            REDIS_SERVER = new RedisServer(REDIS_PORT);
            REDIS_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start embedded Redis for tests", e);
        }
    }

    public static int getPort() {
        return REDIS_PORT;
    }

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("localhost", REDIS_PORT);
        return new LettuceConnectionFactory(configuration);
    }
}
