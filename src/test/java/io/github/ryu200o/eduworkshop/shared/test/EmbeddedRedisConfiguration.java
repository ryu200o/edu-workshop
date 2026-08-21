package io.github.ryu200o.eduworkshop.shared.test;

import java.io.IOException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.util.TestSocketUtils;

import redis.embedded.RedisServer;

/**
 * Starts a single embedded Redis server on a random free TCP port for the test JVM. The port is
 * exposed statically so tests can wire {@code spring.data.redis.*} via {@code @DynamicPropertySource}
 * (OQ-R2). A singleton (static) server avoids port clashes with a local Redis and parallel runs.
 */
@TestConfiguration
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
}
