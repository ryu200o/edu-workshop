package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP test for the Registration read side (learner "My Bookings"), exercising the full
 * stack with a real embedded server: HttpClient → {@link RegistrationQueryController} → shared
 * QueryBus → {@code GetMyRegistrationsQueryHandler} → JOOQ adapter → H2 (Flyway). A published
 * workshop is seeded through the real Workshop/Room HTTP APIs, then registered, and the GET response
 * is asserted to carry the snapshotted title / room / start / end (ADR 0007 single-table pushdown).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RegistrationQueryControllerE2ETest.PermitAllSecurity.class)
class RegistrationQueryControllerE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant START = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(2));

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

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID createRoom(String building) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID publishWorkshop(UUID roomId, String building) throws Exception {
        UUID workshopId = createWorkshop(building, START, END);
        assertThat(post("/api/v1/workshops/" + workshopId + "/plan",
                "{\"roomId\":\"%s\"}".formatted(roomId), Map.of()).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of()).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        return workshopId;
    }

    private UUID createWorkshop(String title, Instant start, Instant end) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "My Bookings E2E", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(title, start, end), Map.of());
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return UUID.fromString(readField(response, "id"));
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    @Test
    void myBookings_returnsSnapshottedBookingsForTheLearner() throws Exception {
        UUID roomId = createRoom("MYBK");
        UUID workshopId = publishWorkshop(roomId, "WS-MYBK");
        UUID userId = UUID.randomUUID();

        HttpResponse<String> registered = post("/api/v1/registrations",
                "{\"workshopId\":\"%s\"}".formatted(workshopId), Map.of("X-User-Id", userId.toString()));
        assertThat(registered.statusCode()).as("register: %s", registered.body()).isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> response = get("/api/v1/registrations", Map.of("X-User-Id", userId.toString()));

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        String body = response.body();
        assertThat(body).contains("WS-MYBK");                  // workshopTitle snapshot
        assertThat(body).contains("MYBK-ROOM-1");              // room name snapshot (seeded by plan)
        assertThat(body).contains(START.toString());           // workshopStartTime snapshot
        assertThat(body).contains(END.toString());             // workshopEndTime snapshot
        assertThat(body).contains("REGISTERED");               // status
        assertThat(body).contains(userId.toString());          // userId
    }

    @Test
    void myBookings_statusFilter_restrictsRows() throws Exception {
        UUID roomId = createRoom("STFLT");
        UUID workshopId = publishWorkshop(roomId, "WS-STFLT");
        UUID userId = UUID.randomUUID();

        assertThat(post("/api/v1/registrations", "{\"workshopId\":\"%s\"}".formatted(workshopId),
                Map.of("X-User-Id", userId.toString())).statusCode()).isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> registeredOnly = get("/api/v1/registrations?status=REGISTERED",
                Map.of("X-User-Id", userId.toString()));
        assertThat(registeredOnly.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(registeredOnly.body()).contains("REGISTERED");

        // REFUNDED is system-initiated and NOT a user-selectable filter → 400.
        HttpResponse<String> refunded = get("/api/v1/registrations?status=REFUNDED",
                Map.of("X-User-Id", userId.toString()));
        assertThat(refunded.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void myBookings_learnerWithoutBookings_returnsEmptyList() throws Exception {
        UUID roomId = createRoom("NOBK");
        publishWorkshop(roomId, "WS-NOBK");

        HttpResponse<String> response = get("/api/v1/registrations", Map.of("X-User-Id", UUID.randomUUID().toString()));

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isEqualTo("[]");
    }
}