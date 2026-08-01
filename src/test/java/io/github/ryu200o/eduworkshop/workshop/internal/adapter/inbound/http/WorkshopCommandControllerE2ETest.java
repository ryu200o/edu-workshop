package io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.http;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP test for the Workshop post-publish write side ({@code RANDOM_PORT}): HttpClient →
 * {@link WorkshopCommandController} → shared CommandBus → Application handlers → JPA/JOOQ adapters →
 * H2 (Flyway). Covers the Phase 2 use cases: cancel, change-room (with kick-out) and adjust-capacity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(WorkshopCommandControllerE2ETest.PermitAllSecurity.class)
class WorkshopCommandControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private UUID createRoom(String building, int capacity) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM", "capacity": %d}
                """.formatted(building, building, capacity), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID createWorkshop(String title, Instant start, Instant end, int capacity) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "E2E workshop", "startTime": "%s", "endTime": "%s", "capacity": %d}
                """.formatted(title, start, end, capacity), Map.of());
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return UUID.fromString(readField(response, "id"));
    }

    private UUID plan(UUID workshopId, UUID roomId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(response.statusCode()).as("plan: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return workshopId;
    }

    private UUID publish(UUID workshopId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(response.statusCode()).as("publish: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return workshopId;
    }

    private void placeUnderMaintenance(UUID roomId) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms/" + roomId + "/maintenance", null, Map.of());
        assertThat(response.statusCode()).as("maintenance: %s", response.body()).isEqualTo(HttpStatus.OK.value());
    }

    private void register(UUID workshopId, UUID userId) throws Exception {
        HttpResponse<String> response = post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), Map.of("X-User-Id", userId.toString()));
        assertThat(response.statusCode()).as("register: %s", response.body()).isEqualTo(HttpStatus.CREATED.value());
    }

    private int activeRegistrations(UUID workshopId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registrations WHERE workshop_id = ? AND status = 'REGISTERED'",
                Integer.class, workshopId.toString());
        return count == null ? 0 : count;
    }

    private String workshopState(UUID workshopId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM workshops WHERE id = ?", String.class, workshopId.toString());
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    @Test
    void cancel_publishedWorkshopWithSeats_flipsAllSeatsToCancelled() throws Exception {
        UUID roomId = createRoom("CXL", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        register(workshopId, UUID.randomUUID());
        register(workshopId, UUID.randomUUID());
        assertThat(activeRegistrations(workshopId)).isEqualTo(2);

        HttpResponse<String> cancelled = post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of());

        assertThat(cancelled.statusCode()).as("cancel: %s", cancelled.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(cancelled.body()).contains(workshopId.toString());
        assertThat(activeRegistrations(workshopId)).isZero();
    }

    @Test
    void cancel_afterStartTime_returnsConflict() throws Exception {
        Instant pastStart = Instant.now().minus(Duration.ofHours(1));
        UUID roomId = createRoom("CXL2", 50);
        UUID workshopId = publish(plan(
                createWorkshop("WS", pastStart, pastStart.plus(Duration.ofHours(2)), 30), roomId));

        HttpResponse<String> cancelled = post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of());

        assertThat(cancelled.statusCode()).as("cancel after start: %s", cancelled.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void changeRoom_toAvailableRoom_returnsOkAndMovesWorkshop() throws Exception {
        UUID oldRoom = createRoom("CRM", 50);
        UUID newRoom = createRoom("CRN", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room: %s", changed.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(changed.body()).contains(newRoom.toString());
    }

    @Test
    void changeRoom_toRoomUnderMaintenance_returnsUnprocessable() throws Exception {
        UUID oldRoom = createRoom("CR1", 50);
        UUID newRoom = createRoom("CR2", 50);
        placeUnderMaintenance(newRoom);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room maintenance: %s", changed.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    @Test
    void changeRoom_toRoomWithOverlappingPublished_returnsConflict() throws Exception {
        UUID oldRoom = createRoom("CR3", 50);
        UUID newRoom = createRoom("CR4", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));
        // Another PUBLISHED workshop already reserves the new room for the same window.
        publish(plan(createWorkshop("WS2", START, END, 30), newRoom));

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room overlap: %s", changed.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void changeRoom_toRoomWithSmallerCapacity_returnsBadRequest() throws Exception {
        UUID oldRoom = createRoom("CR5", 50);
        UUID newRoom = createRoom("CR6", 20);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room capacity: %s", changed.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void changeRoom_kicksOutOverlappingPlannedWorkshop() throws Exception {
        UUID oldRoom = createRoom("CR7", 50);
        UUID newRoom = createRoom("CR8", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));
        // Workshop B is only PLANNED in the new room for the same window → gets kicked to DRAFT.
        UUID plannedB = plan(createWorkshop("WSB", START, END, 20), newRoom);

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room kick-out: %s", changed.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(workshopState(plannedB)).isEqualTo("DRAFT");
    }

    @Test
    void adjustCapacity_belowActiveRegistrations_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("CAP", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        register(workshopId, UUID.randomUUID());
        register(workshopId, UUID.randomUUID());

        HttpResponse<String> adjusted = post("/api/v1/workshops/" + workshopId + "/adjust-capacity",
                """
                {"newCapacity": 1}
                """.formatted(), Map.of());

        assertThat(adjusted.statusCode()).as("adjust-capacity below: %s", adjusted.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    @Test
    void adjustCapacity_valid_returnsOk() throws Exception {
        UUID roomId = createRoom("CAP2", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        HttpResponse<String> adjusted = post("/api/v1/workshops/" + workshopId + "/adjust-capacity",
                """
                {"newCapacity": 40}
                """, Map.of());

        assertThat(adjusted.statusCode()).as("adjust-capacity: %s", adjusted.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(adjusted.body()).contains("\"capacity\":40");
    }

    @Test
    void adjustCapacity_exceedsRoomCapacity_returnsBadRequest() throws Exception {
        UUID roomId = createRoom("CAP3", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        HttpResponse<String> adjusted = post("/api/v1/workshops/" + workshopId + "/adjust-capacity",
                """
                {"newCapacity": 60}
                """, Map.of());

        assertThat(adjusted.statusCode()).as("adjust-capacity exceeds room: %s", adjusted.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
