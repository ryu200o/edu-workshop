package io.github.ryu200o.eduworkshop.attendance.internal.adapter.inbound.scheduling;

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
 * Integration test for the attendance auto-finalize scheduler (Slice 6). The job is enabled via
 * {@code app.attendance.finalize-job.enabled=true} and its {@code scan()} is driven directly
 * (scheduling is otherwise off in the test JVM). A COMPLETED workshop whose Reconciliation Window
 * has elapsed (pinned to 0 minutes) is discovered and its roster is finalized with the SYSTEM actor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.workshop.lifecycle.enabled=false",
                "app.attendance.reconciliation.window-minutes=0",
                "app.attendance.finalize-job.enabled=true"})
class AttendanceAutoFinalizeJobTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttendanceAutoFinalizeJob autoFinalizeJob;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant NOW = Instant.now();
    private static final Instant START = NOW.minus(Duration.ofHours(5));
    private static final Instant END = START.plus(Duration.ofHours(2));

    private IamE2eTestSupport iam;
    private String operatorBearer;
    private String facilityManagerBearer;

    @BeforeEach
    void cleanSchema() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLoginWithRoles("PLANNER").accessToken();
        facilityManagerBearer = iam.registerAndLoginWithRoles("FACILITY_MANAGER").accessToken();
        jdbcTemplate.update("DELETE FROM attendance_entries");
        jdbcTemplate.update("DELETE FROM attendance_records");
        jdbcTemplate.update("DELETE FROM registrations");
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
                {"title": "WS-%s", "description": "E2E auto-finalize", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(UUID.randomUUID(), START, END), Map.of());
        assertThat(created.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        UUID workshopId = IamE2eTestSupport.idFromLocation(created);

        post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        return workshopId;
    }

    @Test
    void scan_discoversCompletedWorkshopAndFinalizesRoster_withSystemActor() throws Exception {
        UUID workshopId = createAndPublishWorkshop("AUTOFIN");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());
        post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                Map.of("Authorization", "Bearer " + trainer.accessToken()));
        post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE workshop_id = ? AND state = 'RECONCILING'",
                Integer.class, workshopId)).isEqualTo(1);

        autoFinalizeJob.scan();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE workshop_id = ? AND state = 'FINALIZED'",
                Integer.class, workshopId)).isEqualTo(1);
    }

    private void registerAndVerify(UUID workshopId, IamE2eTestSupport.TestUser student,
                                    IamE2eTestSupport.TestUser verifier) throws Exception {
        HttpResponse<String> registered = post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), Map.of("Authorization", "Bearer " + student.accessToken()));
        assertThat(registered.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = jdbcTemplate.queryForObject(
                "SELECT id FROM registrations WHERE workshop_id = ? AND user_id = ?",
                UUID.class, workshopId, student.userId());
        HttpResponse<String> verified = post("/api/v1/registrations/verify",
                """
                {"qrReference": "QR-REG-%s"}
                """.formatted(registrationId), Map.of("Authorization", "Bearer " + verifier.accessToken()));
        assertThat(verified.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }
}
