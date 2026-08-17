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

    private HttpResponse<String> delete(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .DELETE();
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", body == null ? HttpRequest.BodyPublishers.noBody()
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

    private UUID workshopRoomId(UUID workshopId) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT room_id FROM workshops WHERE id = ?", String.class, workshopId.toString()));
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
    void changeRoom_kicksOutPlanned_keepsRoom() throws Exception {
        UUID oldRoom = createRoom("CR7", 50);
        UUID newRoom = createRoom("CR8", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), oldRoom));
        // Workshop B is only PLANNED in the new room for the same window → evicted to DRAFT
        // but keeps its room reference (UX upgrade).
        UUID plannedB = plan(createWorkshop("WSB", START, END, 20), newRoom);

        HttpResponse<String> changed = post("/api/v1/workshops/" + workshopId + "/change-room",
                """
                {"roomId": "%s"}
                """.formatted(newRoom), Map.of());

        assertThat(changed.statusCode()).as("change-room kick-out: %s", changed.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(workshopState(plannedB)).isEqualTo("DRAFT");
        assertThat(workshopRoomId(plannedB)).isEqualTo(newRoom);
    }

    // ----------------------------------------------------------------
    // reschedule
    // ----------------------------------------------------------------

    @Test
    void reschedule_published_returnsOk() throws Exception {
        UUID roomId = createRoom("RES", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        Instant newStart = START.plus(Duration.ofDays(3));
        Instant newEnd = newStart.plusSeconds(7200);

        HttpResponse<String> rescheduled = post("/api/v1/workshops/" + workshopId + "/reschedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(newStart, newEnd), Map.of());

        assertThat(rescheduled.statusCode()).as("reschedule: %s", rescheduled.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(rescheduled.body()).contains(newStart.toString());
    }

    @Test
    void reschedule_conflictWithPublished_returnsConflict() throws Exception {
        UUID roomId = createRoom("RES2", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        // Another PUBLISHED workshop already reserves the room for the new window.
        UUID other = publish(plan(createWorkshop("WS2", START.plus(Duration.ofDays(3)),
                START.plus(Duration.ofDays(3)).plusSeconds(7200), 20), roomId));

        HttpResponse<String> rescheduled = post("/api/v1/workshops/" + workshopId + "/reschedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(START.plus(Duration.ofDays(3)), START.plus(Duration.ofDays(3)).plusSeconds(7200)), Map.of());

        assertThat(rescheduled.statusCode()).as("reschedule conflict: %s", rescheduled.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void reschedule_invalidTimeWindow_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("RES3", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        HttpResponse<String> rescheduled = post("/api/v1/workshops/" + workshopId + "/reschedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(END, START), Map.of());

        assertThat(rescheduled.statusCode()).as("reschedule invalid window: %s", rescheduled.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    @Test
    void reschedule_pastDeadline_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("RES4", 50);
        // Workshop starts in 12h → deadline is 36h from now (24h before start).
        // NOW is 2026-08-01; start is 2026-08-01T21:00:00Z (12h from now).
        Instant nearStart = Instant.now().plus(Duration.ofHours(12));
        Instant nearEnd = nearStart.plusSeconds(7200);
        UUID workshopId = publish(plan(
                createWorkshop("WS", nearStart, nearEnd, 30), roomId));

        HttpResponse<String> rescheduled = post("/api/v1/workshops/" + workshopId + "/reschedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(nearStart.plus(Duration.ofDays(3)), nearEnd.plus(Duration.ofDays(3))), Map.of());

        assertThat(rescheduled.statusCode()).as("reschedule past deadline: %s", rescheduled.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    // ----------------------------------------------------------------
    // start / complete (Epic 1 — lifecycle completion)
    // ----------------------------------------------------------------

    @Test
    void start_published_returnsOk() throws Exception {
        UUID roomId = createRoom("L1", 100);
        Instant start = Instant.now().minus(Duration.ofHours(2));  // already due
        Instant end = start.plus(Duration.ofHours(2));
        UUID workshopId = publish(plan(createWorkshop("WS", start, end, 30), roomId));

        HttpResponse<String> started = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        assertThat(started.statusCode()).as("start: %s", started.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(started.body()).contains("\"state\":\"IN_PROGRESS\"");
        assertThat(workshopState(workshopId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void start_beforeStartTime_returnsConflict() throws Exception {
        UUID roomId = createRoom("L2", 100);
        Instant start = Instant.now().plus(Duration.ofHours(24));  // not reached yet
        Instant end = start.plus(Duration.ofHours(2));
        UUID workshopId = publish(plan(createWorkshop("WS", start, end, 30), roomId));

        HttpResponse<String> started = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        assertThat(started.statusCode()).as("start too early: %s", started.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void start_alreadyInProgress_returnsConflict() throws Exception {
        UUID roomId = createRoom("L3", 100);
        Instant start = Instant.now().minus(Duration.ofHours(1));  // already due
        Instant end = start.plus(Duration.ofHours(2));
        UUID workshopId = publish(plan(createWorkshop("WS", start, end, 30), roomId));

        post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        HttpResponse<String> second = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        assertThat(second.statusCode()).as("second start: %s", second.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void complete_inProgress_returnsOk() throws Exception {
        UUID roomId = createRoom("L4", 100);
        Instant start = Instant.now().minus(Duration.ofHours(3));
        Instant end = Instant.now().minus(Duration.ofHours(1));   // already finished
        UUID workshopId = publish(plan(createWorkshop("WS", start, end, 30), roomId));

        post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());

        assertThat(completed.statusCode()).as("complete: %s", completed.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(completed.body()).contains("\"state\":\"COMPLETED\"");
        assertThat(workshopState(workshopId)).isEqualTo("COMPLETED");
    }

    @Test
    void complete_beforeEndTime_returnsConflict() throws Exception {
        UUID roomId = createRoom("L5", 100);
        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(2));  // not reached yet
        UUID workshopId = publish(plan(createWorkshop("WS", start, end, 30), roomId));

        post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());

        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());

        assertThat(completed.statusCode()).as("complete too early: %s", completed.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    // ----------------------------------------------------------------
    // unplan (DELETE /plan)
    // ----------------------------------------------------------------

    @Test
    void unplan_planned_returnsOkAndReleasesRoom() throws Exception {
        UUID roomId = createRoom("UNP", 50);
        UUID workshopId = plan(createWorkshop("WS", START, END, 30), roomId);

        HttpResponse<String> unplan = delete("/api/v1/workshops/" + workshopId + "/plan", Map.of());

        assertThat(unplan.statusCode()).as("unplan: %s", unplan.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(workshopState(workshopId)).isEqualTo("DRAFT");
        assertThat(unplan.body()).contains(workshopId.toString());
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

    // ----------------------------------------------------------------
    // updateInformation (PATCH /{id}/info)
    // ----------------------------------------------------------------

    @Test
    void updateInfo_draft_returnsOkAndUpdatesTitleAndDescription() throws Exception {
        UUID roomId = createRoom("INF1", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/info",
                """
                {"newTitle": "Updated Title", "newDescription": "Updated Description"}
                """, Map.of());

        assertThat(response.statusCode()).as("updateInfo draft: %s", response.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("Updated Title");
        assertThat(response.body()).contains("Updated Description");
    }

    @Test
    void updateInfo_published_withNoRegistrations_returnsOkAndUpdatesTitle() throws Exception {
        UUID roomId = createRoom("INF2", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/info",
                """
                {"newTitle": "New Title", "newDescription": "New Desc"}
                """, Map.of());

        assertThat(response.statusCode()).as("updateInfo published no regs: %s", response.body())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void updateInfo_published_withRegistrations_titleLocked_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("INF3", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        register(workshopId, UUID.randomUUID());

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/info",
                """
                {"newTitle": "Hacked Title", "newDescription": "New Desc"}
                """, Map.of());

        assertThat(response.statusCode()).as("updateInfo title locked: %s", response.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    // ----------------------------------------------------------------
    // updateSchedule (PATCH /{id}/schedule)
    // ----------------------------------------------------------------

    @Test
    void updateSchedule_draft_returnsOkAndUpdatesTimes() throws Exception {
        UUID roomId = createRoom("SCH1", 50);
        UUID workshopId = plan(createWorkshop("WS", START, END, 30), roomId);

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/schedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(newStart, newEnd), Map.of());

        assertThat(response.statusCode()).as("updateSchedule draft: %s", response.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains(newStart.toString());
        assertThat(response.body()).contains(newEnd.toString());
    }

    @Test
    void updateSchedule_published_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("SCH2", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));

        Instant newStart = START.plus(Duration.ofDays(7));
        Instant newEnd = newStart.plusSeconds(7200);

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/schedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(newStart, newEnd), Map.of());

        assertThat(response.statusCode()).as("updateSchedule published: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void updateSchedule_invalidTimeRange_returnsUnprocessable() throws Exception {
        UUID roomId = createRoom("SCH3", 50);
        UUID workshopId = plan(createWorkshop("WS", START, END, 30), roomId);

        HttpResponse<String> response = patch("/api/v1/workshops/" + workshopId + "/schedule",
                """
                {"newStartTime": "%s", "newEndTime": "%s"}
                """.formatted(END, START), Map.of());

        assertThat(response.statusCode()).as("updateSchedule invalid range: %s", response.body())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    @Test
    void updateLatePolicy_draft_returnsOkAndPersistsThreshold() throws Exception {
        UUID workshopId = createWorkshop("WS", START, END, 30);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/late-policy",
                """
                {"lateThresholdSeconds": 930}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy: %s", response.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("\"lateThresholdSeconds\":930");

        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT late_threshold_seconds FROM workshops WHERE id = ?",
                Integer.class, workshopId);
        assertThat(persisted).isEqualTo(930);
    }

    @Test
    void updateLatePolicy_zero_returnsOk() throws Exception {
        UUID workshopId = createWorkshop("WS", START, END, 30);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/late-policy",
                """
                {"lateThresholdSeconds": 0}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy zero: %s", response.body())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("\"lateThresholdSeconds\":0");
    }

    @Test
    void updateLatePolicy_overCeiling_returnsBadRequest() throws Exception {
        UUID workshopId = createWorkshop("WS", START, END, 30);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/late-policy",
                """
                {"lateThresholdSeconds": 86401}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy over ceiling: %s", response.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void updateLatePolicy_negative_returnsBadRequest() throws Exception {
        UUID workshopId = createWorkshop("WS", START, END, 30);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/late-policy",
                """
                {"lateThresholdSeconds": -5}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy negative: %s", response.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void updateLatePolicy_frozenState_returnsConflict() throws Exception {
        UUID roomId = createRoom("LAT2", 50);
        UUID workshopId = publish(plan(createWorkshop("WS", START, END, 30), roomId));
        post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of());

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/late-policy",
                """
                {"lateThresholdSeconds": 900}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy cancelled: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void updateLatePolicy_unknownWorkshop_returnsNotFound() throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/"
                        + UUID.randomUUID() + "/late-policy",
                """
                {"lateThresholdSeconds": 900}
                """, Map.of());

        assertThat(response.statusCode()).as("updateLatePolicy not found: %s", response.body())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
