package io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.IdempotentCommand;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({RedisIdempotencyIntegrationTest.IdempotencyTestConfig.class})
class RedisIdempotencyIntegrationTest {

    private static final String BASE = "/test/idempotent";
    private static final String NIL = "00000000-0000-0000-0000-000000000000";

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AtomicInteger createCounter;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.iam.security.enabled", () -> "false");
        registry.add("app.iam.security-enabled", () -> "false");
    }

    private HttpResponse<String> post(String path, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void missingKey_returns400() throws Exception {
        HttpResponse<String> res = post(BASE + "/create", null);
        assertThat(res.statusCode()).isEqualTo(400);
    }

    @Test
    void firstExecute_returns201AndStoresKey() throws Exception {
        createCounter.set(0);
        HttpResponse<String> res = post(BASE + "/create", "k-create");
        assertThat(res.statusCode()).isEqualTo(201);
        assertThat(res.headers().firstValue("Location").orElse("")).endsWith(BASE + "/123");
        assertThat(createCounter.get()).isEqualTo(1);
    }

    @Test
    void replay_returnsSame201WithoutReexecuting() throws Exception {
        createCounter.set(0);
        HttpResponse<String> r1 = post(BASE + "/create", "k-replay");
        assertThat(r1.statusCode()).isEqualTo(201);
        HttpResponse<String> r2 = post(BASE + "/create", "k-replay");
        assertThat(r2.statusCode()).isEqualTo(201);
        assertThat(r2.headers().firstValue("Location").orElse("")).endsWith(BASE + "/123");
        assertThat(createCounter.get()).isEqualTo(1);
    }

    @Test
    void inProgress_returns409() throws Exception {
        String key = "idempotency:" + NIL + ":POST:" + BASE + "/create:kp";
        redisTemplate.opsForValue().set(key, "IN_PROGRESS");
        HttpResponse<String> res = post(BASE + "/create", "kp");
        assertThat(res.statusCode()).isEqualTo(409);
        redisTemplate.delete(key);
    }

    @Test
    void noContent_returns204() throws Exception {
        HttpResponse<String> res = post(BASE + "/nocontent", "k-nc");
        assertThat(res.statusCode()).isEqualTo(204);
    }

    @Test
    void failure_removesKey() throws Exception {
        String key = "idempotency:" + NIL + ":POST:" + BASE + "/fail:kf";
        HttpResponse<String> res = post(BASE + "/fail", "kf");
        assertThat(res.statusCode()).isGreaterThanOrEqualTo(500);
        assertThat(redisTemplate.opsForValue().get(key)).isNull();
    }

    @TestConfiguration
    static class IdempotencyTestConfig {
        @Bean
        AtomicInteger idempotencyCreateCounter() {
            return new AtomicInteger(0);
        }

        @Bean
        TestController testController(AtomicInteger createCounter) {
            return new TestController(createCounter);
        }

        @Bean
        SecurityFilterChain idempotencyTestSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @RestController
    @RequestMapping(BASE)
    static class TestController {
        private final AtomicInteger createCounter;

        TestController(AtomicInteger createCounter) {
            this.createCounter = createCounter;
        }

        @IdempotentCommand
        @PostMapping("/create")
        ResponseEntity<Void> create() {
            createCounter.incrementAndGet();
            URI location = URI.create(BASE + "/123");
            return ResponseEntity.created(location).build();
        }

        @IdempotentCommand
        @PostMapping("/nocontent")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }

        @IdempotentCommand
        @PostMapping("/fail")
        ResponseEntity<Void> fail() {
            throw new IllegalStateException("boom");
        }
    }
}
