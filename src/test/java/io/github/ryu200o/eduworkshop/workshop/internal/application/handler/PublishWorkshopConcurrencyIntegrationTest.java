package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.security.IamE2eTestSupport;

import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency integration test (ADR 0015 / ADR 0008): two requests concurrently publish two
 * {@code PLANNED} workshops in the same room + overlapping window. The set-based pessimistic lock
 * ({@code loadPublishedAndPlannedOverlappingWithLock}, lock-set-first) serializes the operations:
 * exactly one request wins ({@code 200}), the other is hard-blocked with a {@code RoomConflictException}
 * surfaced as {@code 409}. No double-booking is possible.
 *
 * <p>Calls are authenticated through the real IAM flow (register → login → Bearer, plan §7 Slice 5);
 * the removed permit-all test chain is gone.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublishWorkshopConcurrencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private IamE2eTestSupport iam;
    private String operatorBearer;

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLogin().accessToken();
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + operatorBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private UUID createRoom(String prefix) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 5, "name": "%s-CONC", "capacity": 50}
                """.formatted(prefix, prefix));
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
    }

    private UUID createWorkshop(String title, String start, String end) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "CONC workshop", "startTime": "%s", "endTime": "%s", "capacity": 20}
                """.formatted(title, start, end));
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
    }

    private UUID plan(UUID workshopId, UUID roomId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId));
        assertThat(response.statusCode()).as("plan: %s", response.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private int publishStatusCode(UUID workshopId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/publish", null);
        return response.statusCode();
    }

    @Test
    void concurrentPublish_sameRoomAndWindow_exactlyOneWins() throws Exception {
        String start = "2026-12-01T09:00:00Z";
        String end = "2026-12-01T11:00:00Z";
        UUID roomId = createRoom("CPA");
        UUID workshopA = plan(createWorkshop("CONC-A", start, end), roomId);
        UUID workshopB = plan(createWorkshop("CONC-B", start, end), roomId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> publishA = executor.submit(() -> {
            ready.countDown();
            go.await();
            return publishStatusCode(workshopA);
        });
        Future<Integer> publishB = executor.submit(() -> {
            ready.countDown();
            go.await();
            return publishStatusCode(workshopB);
        });

        ready.await();
        go.countDown();
        int statusA = publishA.get();
        int statusB = publishB.get();

        assertThat(List.of(statusA, statusB)).as("one wins (204), one conflicts").containsExactlyInAnyOrder(
                HttpStatus.NO_CONTENT.value(), HttpStatus.CONFLICT.value());

        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workshops WHERE state = 'PUBLISHED' AND room_id = ?",
                Integer.class, roomId);
        assertThat(published).as("exactly one PUBLISHED for the contested room").isEqualTo(1);
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }
}
