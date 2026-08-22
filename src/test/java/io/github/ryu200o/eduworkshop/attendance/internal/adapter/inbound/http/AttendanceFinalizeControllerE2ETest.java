package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.http;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP tests for the dual-trigger roster finalization (ADR 0019 §4, Slice 6):
 * the manual AUDITOR endpoint {@code POST /api/v1/workshops/{workshopId}/attendance/finalize}.
 *
 * <p>The reconciliation window is pinned to {@code 0} minutes so a just-completed workshop is
 * immediately finalizable (the window opens on completion and elapses at once), keeping the test
 * deterministic without time travel. The lifecycle scheduler is disabled so state transitions are
 * driven manually.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.workshop.lifecycle.enabled=false", "app.attendance.reconciliation.window-minutes=0"})
class AttendanceFinalizeControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant NOW = Instant.now();
    private static final Instant START = NOW.minus(Duration.ofHours(5));
    private static final Instant END = START.plus(Duration.ofHours(2));

    private IamE2eTestSupport iam;
    private String operatorBearer;
    private String facilityManagerBearer;
    private String auditorBearer;

    @BeforeEach
    void cleanSchema() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLoginWithRoles("PLANNER").accessToken();
        facilityManagerBearer = iam.registerAndLoginWithRoles("FACILITY_MANAGER").accessToken();
        auditorBearer = iam.registerAndLoginWithRoles("AUDITOR").accessToken();
        jdbcTemplate.update("DELETE FROM attendance_entries");
        jdbcTemplate.update("DELETE FROM attendance_records");
        jdbcTemplate.update("DELETE FROM registrations");
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        return postWithKey(path, body, headers, UUID.randomUUID().toString());
    }

    private HttpResponse<String> postWithKey(String path, String body, Map<String, String> headers,
                                            String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        withAuth(headers).forEach(builder::header);
        builder.header("Idempotency-Key", idempotencyKey);
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

    private UUID createAndPublishWorkshop(String building) throws Exception {
        HttpResponse<String> room = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building),
                Map.of("Authorization", "Bearer " + facilityManagerBearer));
        assertThat(room.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        UUID roomId = IamE2eTestSupport.idFromLocation(room);

        HttpResponse<String> created = post("/api/v1/workshops",
                """
                {"title": "WS-%s", "description": "E2E finalize workshop", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(UUID.randomUUID(), START, END), Map.of());
        assertThat(created.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        UUID workshopId = IamE2eTestSupport.idFromLocation(created);

        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private void startWorkshop(UUID workshopId) throws Exception {
        HttpResponse<String> started = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());
        assertThat(started.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private void completeWorkshop(UUID workshopId) throws Exception {
        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());
        assertThat(completed.statusCode()).as("complete: %s", completed.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private void registerAndVerify(UUID workshopId, IamE2eTestSupport.TestUser student,
                                   IamE2eTestSupport.TestUser verifier) throws Exception {
        HttpResponse<String> registered = post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), student.bearer());
        assertThat(registered.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = jdbcTemplate.queryForObject(
                "SELECT id FROM registrations WHERE workshop_id = ? AND user_id = ?",
                UUID.class, workshopId, student.userId());
        HttpResponse<String> verified = post("/api/v1/registrations/verify",
                """
                {"qrReference": "QR-REG-%s"}
                """.formatted(registrationId), verifier.bearer());
        assertThat(verified.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private void mark(UUID workshopId, IamE2eTestSupport.TestUser student,
                      IamE2eTestSupport.TestUser trainer) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),                 Map.of("Authorization", "Bearer " + trainer.accessToken()));
        assertThat(response.statusCode()).as("mark: %s", response.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void finalize_byAuditor_whenWorkshopCompleted_returnsNoContentAndLocksRoster() throws Exception {
        UUID workshopId = createAndPublishWorkshop("FINOK");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);
        mark(workshopId, student, trainer);
        completeWorkshop(workshopId);

        HttpResponse<String> finalized = post("/api/v1/workshops/" + workshopId + "/attendance/finalize", null,
                Map.of("Authorization", "Bearer " + auditorBearer));

        assertThat(finalized.statusCode()).as("finalize: %s", finalized.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        // The roster is now locked: no further appeals/adjustments are accepted.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE workshop_id = ? AND state = 'FINALIZED'",
                Integer.class, workshopId)).isEqualTo(1);
    }

    @Test
    void finalize_byUserRole_returnsForbidden() throws Exception {
        UUID workshopId = createAndPublishWorkshop("FINUSER");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);
        mark(workshopId, student, trainer);
        completeWorkshop(workshopId);

        HttpResponse<String> finalized = post("/api/v1/workshops/" + workshopId + "/attendance/finalize", null,
                Map.of("Authorization", "Bearer " + student.accessToken()));

        assertThat(finalized.statusCode()).as("finalize by user: %s", finalized.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void finalize_byPlannerRole_returnsForbidden() throws Exception {
        UUID workshopId = createAndPublishWorkshop("FINPLAN");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);
        mark(workshopId, student, trainer);
        completeWorkshop(workshopId);

        HttpResponse<String> finalized = post("/api/v1/workshops/" + workshopId + "/attendance/finalize", null,
                Map.of("Authorization", "Bearer " + operatorBearer));

        assertThat(finalized.statusCode()).as("finalize by planner: %s", finalized.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void finalize_idempotentReplay_withSameKey_returnsNoContent() throws Exception {
        UUID workshopId = createAndPublishWorkshop("FINIDEM");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);
        mark(workshopId, student, trainer);
        completeWorkshop(workshopId);

        String key = UUID.randomUUID().toString();
        HttpResponse<String> first = postWithKey("/api/v1/workshops/" + workshopId + "/attendance/finalize", null,
                Map.of("Authorization", "Bearer " + auditorBearer), key);
        HttpResponse<String> replay = postWithKey("/api/v1/workshops/" + workshopId + "/attendance/finalize", null,
                Map.of("Authorization", "Bearer " + auditorBearer), key);

        assertThat(first.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(replay.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE workshop_id = ? AND state = 'FINALIZED'",
                Integer.class, workshopId)).isEqualTo(1);
    }
}
