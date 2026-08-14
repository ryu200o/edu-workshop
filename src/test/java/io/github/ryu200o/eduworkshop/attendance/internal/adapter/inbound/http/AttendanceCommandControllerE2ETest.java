package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

import org.junit.jupiter.api.BeforeEach;
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
 * End-to-end HTTP tests for the Attendance module — full stack against a real embedded server
 * ({@code RANDOM_PORT}): HttpClient → controllers → shared Command/Query bus → Application handlers
 * → JPA/JOOQ adapters → H2 (Flyway).
 *
 * <p>Workshops are driven through the real Workshop/Room HTTP APIs (plan → publish → start →
 * complete), so the {@code WorkshopCompletedIntegrationEvent} flows through the outbox
 * (ADR 0011) into {@code AttendanceWorkshopCompletedEventHandler}. The {@code VERIFIED}
 * registration gate (OQ-14) is exercised by seeding {@code VERIFIED} / plain {@code REGISTERED}
 * rows directly in the DB — no VERIFIED transition exists yet (SA directive contract tests).</p>
 *
 * <p>The workshop lifecycle scheduler is disabled ({@code app.workshop.lifecycle.enabled=false})
 * so the tests drive state transitions manually and deterministically. A permit-all
 * {@link SecurityFilterChain} is contributed by this test only, because
 * {@code spring-boot-starter-security} is on the classpath without a real auth config.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.workshop.lifecycle.enabled=false"})
@Import(AttendanceCommandControllerE2ETest.PermitAllSecurity.class)
class AttendanceCommandControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant NOW = Instant.now();
    private static final Instant START = NOW.minus(Duration.ofHours(5));
    private static final Instant END = START.plus(Duration.ofHours(2));
    private static final UUID TRAINER = UUID.randomUUID();

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

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM attendance_entries");
        jdbcTemplate.update("DELETE FROM attendance_records");
        jdbcTemplate.update("DELETE FROM registrations");
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private UUID createRoom(String building) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        return UUID.fromString(readField(response.body(), "id"));
    }

    private UUID createAndPublishWorkshop(String building, boolean startNow) throws Exception {
        UUID roomId = createRoom(building);
        HttpResponse<String> created = post("/api/v1/workshops",
                """
                {"title": "WS-%s", "description": "E2E attendance workshop", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(UUID.randomUUID(), START, END), Map.of());
        assertThat(created.statusCode()).as("create workshop: %s", created.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID workshopId = UUID.fromString(readField(created.body(), "id"));

        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan workshop: %s", planned.body()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish workshop: %s", published.body()).isEqualTo(HttpStatus.OK.value());
        if (startNow) {
            HttpResponse<String> started = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());
            assertThat(started.statusCode()).as("start workshop: %s", started.body()).isEqualTo(HttpStatus.OK.value());
        }
        return workshopId;
    }

    private void seedRegistration(UUID workshopId, UUID studentId, String status) {
        jdbcTemplate.update("""
                INSERT INTO registrations (id, workshop_id, user_id, status, workshop_start_time, registered_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), workshopId, studentId, status, START, NOW);
    }

    private UUID mark(UUID workshopId, UUID studentId) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(studentId),
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", TRAINER.toString()));
        assertThat(response.statusCode()).as("mark: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> roster = get("/api/v1/workshops/" + workshopId + "/attendance");
        return UUID.fromString(readField(roster.body(), "recordId"));
    }

    private static String readField(String body, String field) {
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    @Test
    void mark_verifiedRegistration_happyPathAndRoster() throws Exception {
        UUID workshopId = createAndPublishWorkshop("VERIF", true);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "VERIFIED");

        mark(workshopId, studentId);

        HttpResponse<String> roster = get("/api/v1/workshops/" + workshopId + "/attendance");
        assertThat(roster.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(roster.body()).contains("\"total\":1").contains("\"present\":1");
    }

    @Test
    void mark_nonVerifiedRegistration_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("NOVER", true);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "REGISTERED");

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(studentId),
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", TRAINER.toString()));

        assertThat(response.statusCode()).as("mark non-verified: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body()).contains("not verified");
    }

    @Test
    void mark_duplicateStudentIdInBatch_returnsBadRequest() throws Exception {
        UUID workshopId = createAndPublishWorkshop("DUPLISTU", true);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "VERIFIED");

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [
                    {"studentId": "%s", "status": "PRESENT", "note": null},
                    {"studentId": "%s", "status": "LATE", "note": "contradicts previous"}
                ]}
                """.formatted(studentId, studentId),
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", TRAINER.toString()));

        assertThat(response.statusCode()).as("duplicate studentId: %s", response.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.body()).contains("Duplicate studentId");
        // Nothing was persisted — the command invariant failed before any mutation.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE workshop_id = ?", Integer.class, workshopId))
                .isZero();
    }

    @Test
    void mark_workshopNotInProgress_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("NOSTART", false);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "VERIFIED");

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(studentId),
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", TRAINER.toString()));

        assertThat(response.statusCode()).as("mark not in progress: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void mark_nonTrainerRole_returnsForbidden() throws Exception {
        UUID workshopId = createAndPublishWorkshop("ROLE", true);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "VERIFIED");

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(studentId),
                Map.of("X-Actor-Role", "STUDENT", "X-User-Id", studentId.toString()));

        assertThat(response.statusCode()).as("mark by student: %s", response.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void completeFlow_appealAndAdjustWithinReconciliationWindow() throws Exception {
        UUID workshopId = createAndPublishWorkshop("APPEAL", true);
        UUID studentId = UUID.randomUUID();
        seedRegistration(workshopId, studentId, "VERIFIED");

        HttpResponse<String> marked = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(studentId),
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", TRAINER.toString()));
        assertThat(marked.statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> ledger = get("/api/v1/workshops/" + workshopId + "/attendance");
        String recordId = readField(ledger.body(), "recordId");

        // Complete → outbox delivers WorkshopCompletedIntegrationEvent → window opens (RECONCILING).
        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());
        assertThat(completed.statusCode()).as("complete: %s", completed.body()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> appealed = post("/api/v1/attendance-records/" + recordId + "/appeal",
                """
                {"reason": "I was present but marked late", "evidenceReference": "evidence://cam-1"}
                """,
                Map.of("X-Actor-Role", "STUDENT", "X-User-Id", studentId.toString()));
        assertThat(appealed.statusCode()).as("appeal: %s", appealed.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(appealed.body()).contains("current result unchanged");

        HttpResponse<String> adjusted = post("/api/v1/attendance-records/" + recordId + "/adjust",
                """
                {"newStatus": "PRESENT", "reason": "CCTV confirms presence", "evidenceReference": "evidence://cam-1"}
                """,
                Map.of("X-Actor-Role", "AUDITOR", "X-User-Id", UUID.randomUUID().toString()));
        assertThat(adjusted.statusCode()).as("adjust: %s", adjusted.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(adjusted.body()).contains("PRESENT");

        HttpResponse<String> fullLedger = get("/api/v1/attendance-records/" + recordId);
        assertThat(fullLedger.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(fullLedger.body()).contains("MARK").contains("APPEAL").contains("AUDITOR_ADJUST");
    }

    @Test
    void ledger_missingRecord_returnsNotFound() throws Exception {
        HttpResponse<String> response = get("/api/v1/attendance-records/" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}