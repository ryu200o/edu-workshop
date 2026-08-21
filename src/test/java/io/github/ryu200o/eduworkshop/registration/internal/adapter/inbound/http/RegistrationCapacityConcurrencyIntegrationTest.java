package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.shared.security.IamE2eTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Epic 2 capacity gate under real concurrency. Two learners register <em>simultaneously</em>
 * for a workshop whose capacity is exactly one. Without a serialization point both requests would read
 * {@code countActiveByWorkshop = 0}, both pass {@code 0 < 1} and both insert → over-booking.
 *
 * <p>The {@code PESSIMISTIC_WRITE} lock-anchor on the {@code workshops} row
 * ({@code WorkshopExposeAPI.lockForRegistration} → {@code loadByIdWithLock}, ADR 0015) serializes the
 * two requests: exactly one wins the seat (201 CREATED) and the other is rejected with
 * 409 CONFLICT {@code WorkshopCapacityExceededException}. A retry loop absorbs scheduler contention so
 * the outcome is asserted on the final state, not on a fragile first-attempt 409.</p>
 *
 * <p>Each learner is a real IAM user carrying its own Bearer token (plan §7 Slice 5); the removed
 * permit-all test chain is gone.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationCapacityConcurrencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant START = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(2));

    private IamE2eTestSupport iam;
    private String operatorBearer;

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLogin().accessToken();
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        withAuth(headers).forEach(builder::header);
        builder.header("Idempotency-Key", UUID.randomUUID().toString());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, String> withAuth(Map<String, String> headers) {
        if (headers.containsKey("Authorization")) {
            return headers;
        }
        Map<String, String> effective = new HashMap<>(headers);
        effective.put("Authorization", "Bearer " + operatorBearer);
        return effective;
    }

    private UUID createRoom(String building) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
    }

    private UUID publishSingleSeatWorkshop(String building) throws Exception {
        UUID roomId = createRoom(building);
        HttpResponse<String> created = post("/api/v1/workshops",
                """
                {"title": "WS-%s", "description": "concurrency", "startTime": "%s", "endTime": "%s", "capacity": 1}
                """.formatted(UUID.randomUUID(), START, END), Map.of());
        assertThat(created.statusCode()).as("create workshop: %s", created.body()).isEqualTo(HttpStatus.CREATED.value());
        UUID workshopId = IamE2eTestSupport.idFromLocation(created);

        assertThat(post("/api/v1/workshops/" + workshopId + "/plan",
                "{\"roomId\":\"%s\"}".formatted(roomId), Map.of()).statusCode())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of()).statusCode())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    private int register(UUID workshopId, IamE2eTestSupport.TestUser learner) throws Exception {
        HttpResponse<String> response = post("/api/v1/registrations",
                "{\"workshopId\":\"%s\"}".formatted(workshopId), learner.bearer());
        return response.statusCode();
    }

    @Test
    void twoConcurrentRegistrationsForSingleSeat_onlyOneWins() throws Exception {
        UUID workshopId = publishSingleSeatWorkshop("CONC");
        IamE2eTestSupport.TestUser learnerA = iam.registerAndLogin();
        IamE2eTestSupport.TestUser learnerB = iam.registerAndLogin();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var futureA = executor.submit(() -> {
                startGate.await();
                return register(workshopId, learnerA);
            });
            var futureB = executor.submit(() -> {
                startGate.await();
                return register(workshopId, learnerB);
            });

            startGate.countDown();

            int statusA = futureA.get(30, TimeUnit.SECONDS);
            int statusB = futureB.get(30, TimeUnit.SECONDS);

            long created = java.util.stream.Stream.of(statusA, statusB)
                    .filter(s -> s == HttpStatus.CREATED.value()).count();
            long conflicts = java.util.stream.Stream.of(statusA, statusB)
                    .filter(s -> s == HttpStatus.CONFLICT.value()).count();

            assertThat(created).as("exactly one seat won").isEqualTo(1);
            assertThat(conflicts).as("the other learner is rejected").isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}