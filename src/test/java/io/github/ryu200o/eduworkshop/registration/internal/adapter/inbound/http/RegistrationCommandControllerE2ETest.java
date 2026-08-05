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
 * End-to-end HTTP test for the Registration write side, exercising the full stack against a real
 * embedded server ({@code RANDOM_PORT}): HttpClient → {@link RegistrationCommandController} →
 * shared CommandBus → Application handlers → JPA/JOOQ adapters → H2 (Flyway). A published workshop
 * is seeded through the Workshop/Room HTTP APIs (real use-case orchestration, not mocks).
 *
 * <p>A permit-all {@link SecurityFilterChain} is contributed by this test only, because
 * {@code spring-boot-starter-security} is on the classpath without a real auth config.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RegistrationCommandControllerE2ETest.PermitAllSecurity.class)
class RegistrationCommandControllerE2ETest {

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

    private UUID createRoom(String building) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID createWorkshop(String title, Instant start, Instant end) throws Exception {
        return createWorkshop(title, start, end, 30);
    }

    private UUID createWorkshop(String title, Instant start, Instant end, int capacity) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "E2E seeding workshop", "startTime": "%s", "endTime": "%s", "capacity": %d}
                """.formatted(title, start, end, capacity), Map.of());
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID publishWorkshop(UUID roomId) throws Exception {
        return publishWorkshop(roomId, 30);
    }

    private UUID publishWorkshop(UUID roomId, int capacity) throws Exception {
        UUID workshopId = createWorkshop("WS-" + UUID.randomUUID(), START, END, capacity);
        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan workshop: %s", planned.body()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish workshop: %s", published.body()).isEqualTo(HttpStatus.OK.value());
        return workshopId;
    }

    private HttpResponse<String> register(UUID workshopId, UUID userId) throws Exception {
        return post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), Map.of("X-User-Id", userId.toString()));
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    @Test
    void registerAndCancel_happyPath() throws Exception {
        UUID roomId = createRoom("HAPPY");
        UUID workshopId = publishWorkshop(roomId);
        UUID userId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, userId);
        assertThat(registered.statusCode()).as("register: %s", registered.body()).isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                Map.of("X-User-Id", userId.toString()));
        assertThat(cancelled.statusCode()).as("cancel: %s", cancelled.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(cancelled.body()).contains(registrationId.toString());
    }

    @Test
    void register_duplicateActiveRegistration_returnsConflict() throws Exception {
        UUID roomId = createRoom("DUP");
        UUID workshopId = publishWorkshop(roomId);
        UUID userId = UUID.randomUUID();

        assertThat(register(workshopId, userId).statusCode()).isEqualTo(HttpStatus.CREATED.value());
        HttpResponse<String> duplicate = register(workshopId, userId);
        assertThat(duplicate.statusCode()).as("duplicate register: %s", duplicate.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void register_workshopNotPublished_returnsConflict() throws Exception {
        UUID roomId = createRoom("DRAFT");
        UUID workshopId = createWorkshop("WS-" + UUID.randomUUID(), START, END);
        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> registered = register(workshopId, UUID.randomUUID());
        assertThat(registered.statusCode()).as("register draft: %s", registered.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void register_whenCapacityFull_returnsConflict() throws Exception {
        UUID roomId = createRoom("FULL");
        UUID workshopId = publishWorkshop(roomId, 1);

        UUID firstUser = UUID.randomUUID();
        assertThat(register(workshopId, firstUser).statusCode()).as("first seat: register").isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> second = register(workshopId, UUID.randomUUID());
        assertThat(second.statusCode()).as("second seat beyond capacity").isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(second.body()).contains("capacity=1");
    }

    @Test
    void register_missingUserHeader_returnsBadRequest() throws Exception {
        UUID roomId = createRoom("NOUSER");
        UUID workshopId = publishWorkshop(roomId);

        HttpResponse<String> response = post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), Map.of());
        assertThat(response.statusCode()).as("register without header: %s", response.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void cancel_notOwner_returnsForbidden() throws Exception {
        UUID roomId = createRoom("OWNER");
        UUID workshopId = publishWorkshop(roomId);
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, owner);
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                Map.of("X-User-Id", stranger.toString()));
        assertThat(cancelled.statusCode()).as("cancel by stranger: %s", cancelled.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void cancel_afterCancellationDeadline_returnsBadRequest() throws Exception {
        Instant soonStart = Instant.now().plus(Duration.ofHours(10));
        UUID roomId = createRoom("DEADLINE");
        UUID workshopId = createWorkshop("WS-" + UUID.randomUUID(), soonStart, soonStart.plus(Duration.ofHours(2)));
        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish: %s", published.body()).isEqualTo(HttpStatus.OK.value());
        UUID userId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, userId); // starts within 24h → past deadline
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                Map.of("X-User-Id", userId.toString()));
        assertThat(cancelled.statusCode()).as("cancel past deadline: %s", cancelled.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
