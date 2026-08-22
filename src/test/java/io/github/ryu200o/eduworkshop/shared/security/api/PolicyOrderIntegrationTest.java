package io.github.ryu200o.eduworkshop.shared.security.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ryu200o.eduworkshop.shared.infrastructure.idempotency.api.Idempotent;
import io.github.ryu200o.eduworkshop.shared.security.IamE2eTestSupport;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanManageRooms;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the required aspect ordering (ADR 0023 / SA directive): Method Security runs first
 * (order 0), Idempotency Aspect second (order 1). A caller without the required role must receive
 * 403 and the Idempotency Aspect must NOT reserve a Redis key (zero-touch Redis).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PolicyOrderIntegrationTest.PolicyOrderTestConfig.class)
class PolicyOrderIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void insufficientRole_returns403AndDoesNotReserveRedisKey() throws Exception {
        var support = new IamE2eTestSupport(port, http, objectMapper, jdbcTemplate);
        var user = support.registerAndLogin();

        Set<String> seeded = redisTemplate.keys("idempotency:*");
        if (seeded != null && !seeded.isEmpty()) {
            redisTemplate.delete(seeded);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/test/rbac-order/protected"))
                .header("Authorization", "Bearer " + user.accessToken())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        Set<String> keys = redisTemplate.keys("idempotency:*");
        assertThat(keys).isEmpty();
    }

    @TestConfiguration
    static class PolicyOrderTestConfig {
        @Bean
        PolicyOrderTestController policyOrderTestController() {
            return new PolicyOrderTestController();
        }
    }

    @RestController
    @RequestMapping("/test/rbac-order")
    @CanManageRooms
    static class PolicyOrderTestController {

        @Idempotent
        @PostMapping("/protected")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.ok().build();
        }
    }
}
