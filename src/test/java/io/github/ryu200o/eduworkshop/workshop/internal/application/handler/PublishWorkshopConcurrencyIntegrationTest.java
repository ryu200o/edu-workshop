package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PublishWorkshopConcurrencyIntegrationTest.PermitAllSecurity.class)
class PublishWorkshopConcurrencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @TestConfiguration
    static class PermitAllSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
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
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID createWorkshop(String title, String start, String end) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "CONC workshop", "startTime": "%s", "endTime": "%s", "capacity": 20}
                """.formatted(title, start, end));
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID plan(UUID workshopId, UUID roomId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId));
        assertThat(response.statusCode()).as("plan: %s", response.body()).isEqualTo(HttpStatus.OK.value());
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

        assertThat(List.of(statusA, statusB)).as("one wins, one conflicts").containsExactlyInAnyOrder(
                HttpStatus.OK.value(), HttpStatus.CONFLICT.value());

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
