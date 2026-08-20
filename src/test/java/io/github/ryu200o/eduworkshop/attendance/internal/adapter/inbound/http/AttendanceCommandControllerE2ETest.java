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
 * End-to-end HTTP tests for the Attendance module — full stack against a real embedded server
 * ({@code RANDOM_PORT}): HttpClient → controllers → shared Command/Query bus → Application handlers
 * → JPA/JOOQ adapters → H2 (Flyway).
 *
 * <p>Workshops are driven through the real Workshop/Room HTTP APIs (plan → publish → start →
 * complete), so the {@code WorkshopCompletedIntegrationEvent} flows through the outbox
 * (ADR 0011) into {@code AttendanceWorkshopCompletedEventHandler}. The {@code VERIFIED}
 * registration gate (OQ-14) is exercised through the real Registration API: a student is registered
 * and then verified via the verify endpoint (Epic 3C, thin QR seam) before the workshop starts —
 * the Attendance module reads the verified seat through the Registration reader.</p>
 *
 * <p>The workshop lifecycle scheduler is disabled ({@code app.workshop.lifecycle.enabled=false})
 * so the tests drive state transitions manually and deterministically. All actors are real IAM
 * users carrying Bearer tokens (register → login → Bearer, plan §7 Slice 5); contextual authority
 * follows OQ-2 — {@code mark} needs a {@code PLANNER}/{@code ADMIN}, {@code adjust} needs
 * {@code AUDITOR}, check-in/appeal map any principal to {@code STUDENT}. The removed permit-all
 * test chain is gone.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.workshop.lifecycle.enabled=false"})
class AttendanceCommandControllerE2ETest {

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

    @BeforeEach
    void cleanSchema() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLogin().accessToken();
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
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + operatorBearer)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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

    private UUID createAndPublishWorkshop(String building) throws Exception {
        UUID roomId = createRoom(building);
        HttpResponse<String> created = post("/api/v1/workshops",
                """
                {"title": "WS-%s", "description": "E2E attendance workshop", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(UUID.randomUUID(), START, END), Map.of());
        assertThat(created.statusCode()).as("create workshop: %s", created.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID workshopId = IamE2eTestSupport.idFromLocation(created);

        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan workshop: %s", planned.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish workshop: %s", published.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private void startWorkshop(UUID workshopId) throws Exception {
        HttpResponse<String> started = post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());
        assertThat(started.statusCode()).as("start workshop: %s", started.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private void registerStudent(UUID workshopId, IamE2eTestSupport.TestUser student) throws Exception {
        HttpResponse<String> registered = post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId),
                student.bearer());
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
    }

    private void registerAndVerify(UUID workshopId, IamE2eTestSupport.TestUser student,
                                   IamE2eTestSupport.TestUser verifier) throws Exception {
        registerStudent(workshopId, student);
        UUID registrationId = jdbcTemplate.queryForObject(
                "SELECT id FROM registrations WHERE workshop_id = ? AND user_id = ?",
                UUID.class, workshopId, student.userId());
        HttpResponse<String> verified = post("/api/v1/registrations/verify",
                """
                {"qrReference": "QR-REG-%s"}
                """.formatted(registrationId),
                verifier.bearer());
        assertThat(verified.statusCode()).as("verify: %s", verified.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private UUID mark(UUID workshopId, IamE2eTestSupport.TestUser student,
                      IamE2eTestSupport.TestUser trainer) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                trainer.bearer());
        assertThat(response.statusCode()).as("mark: %s", response.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> roster = get("/api/v1/workshops/" + workshopId + "/attendance");
        return UUID.fromString(readField(roster.body(), "recordId"));
    }

    private HttpResponse<String> checkIn(UUID workshopId, IamE2eTestSupport.TestUser student) throws Exception {
        return post("/api/v1/workshops/" + workshopId + "/attendance/check-in",
                """
                {"qrReference": "QR-REF-%s"}
                """.formatted(UUID.randomUUID()),
                student.bearer());
    }

    private static String readField(String body, String field) {
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    @Test
    void mark_verifiedRegistration_happyPathAndRoster() throws Exception {
        UUID workshopId = createAndPublishWorkshop("VERIF");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        mark(workshopId, student, trainer);

        HttpResponse<String> roster = get("/api/v1/workshops/" + workshopId + "/attendance");
        assertThat(roster.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(roster.body()).contains("\"total\":1").contains("\"present\":1");
    }

    @Test
    void mark_nonVerifiedRegistration_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("NOVER");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        registerStudent(workshopId, student);
        startWorkshop(workshopId);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                trainer.bearer());

        assertThat(response.statusCode()).as("mark non-verified: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body()).contains("not verified");
    }

    @Test
    void mark_duplicateStudentIdInBatch_returnsBadRequest() throws Exception {
        UUID workshopId = createAndPublishWorkshop("DUPLISTU");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [
                    {"studentId": "%s", "status": "PRESENT", "note": null},
                    {"studentId": "%s", "status": "LATE", "note": "contradicts previous"}
                ]}
                """.formatted(student.userId(), student.userId()),
                trainer.bearer());

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
        UUID workshopId = createAndPublishWorkshop("NOSTART");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                trainer.bearer());

        assertThat(response.statusCode()).as("mark not in progress: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void mark_nonTrainerRole_returnsForbidden() throws Exception {
        UUID workshopId = createAndPublishWorkshop("ROLE");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        // A USER-role principal has no PLANNER/ADMIN authority (OQ-2) → 403 before the handler.
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                student.bearer());

        assertThat(response.statusCode()).as("mark by student: %s", response.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void completeFlow_appealAndAdjustWithinReconciliationWindow() throws Exception {
        UUID workshopId = createAndPublishWorkshop("APPEAL");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        IamE2eTestSupport.TestUser auditor = iam.registerAndLoginWithRoles("AUDITOR");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> marked = post("/api/v1/workshops/" + workshopId + "/attendance/mark",
                """
                {"items": [{"studentId": "%s", "status": "PRESENT", "note": null}]}
                """.formatted(student.userId()),
                trainer.bearer());
        assertThat(marked.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> ledger = get("/api/v1/workshops/" + workshopId + "/attendance");
        String recordId = readField(ledger.body(), "recordId");

        // Complete → outbox delivers WorkshopCompletedIntegrationEvent → window opens (RECONCILING).
        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());
        assertThat(completed.statusCode()).as("complete: %s", completed.body()).isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> appealed = post("/api/v1/attendance-records/" + recordId + "/appeal",
                """
                {"reason": "I was present but marked late", "evidenceReference": "evidence://cam-1"}
                """,
                student.bearer());
        assertThat(appealed.statusCode()).as("appeal: %s", appealed.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> adjusted = post("/api/v1/attendance-records/" + recordId + "/adjust",
                """
                {"newStatus": "PRESENT", "reason": "CCTV confirms presence", "evidenceReference": "evidence://cam-1"}
                """,
                auditor.bearer());
        assertThat(adjusted.statusCode()).as("adjust: %s", adjusted.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> fullLedger = get("/api/v1/attendance-records/" + recordId);
        assertThat(fullLedger.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(fullLedger.body()).contains("MARK").contains("APPEAL").contains("AUDITOR_ADJUST");
    }

    @Test
    void ledger_missingRecord_returnsNotFound() throws Exception {
        HttpResponse<String> response = get("/api/v1/attendance-records/" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void checkIn_verifiedRegistration_createsLateRecordWithStudentRoleInLedger() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKOK");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> response = checkIn(workshopId, student);

        assertThat(response.statusCode()).as("check-in: %s", response.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        // START is 5h before now and the late threshold is startTime + 15min → LATE.
        String recordId = readField(
                get("/api/v1/workshops/" + workshopId + "/attendance").body(), "recordId");

        HttpResponse<String> ledger = get("/api/v1/attendance-records/" + recordId);
        assertThat(ledger.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(ledger.body()).contains("\"action\":\"MARK\"").contains("\"actorRole\":\"STUDENT\"")
                .contains("\"result\":\"LATE\"").contains("\"state\":\"OPEN\"");
    }

    @Test
    void checkIn_nonVerifiedRegistration_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKNOVER");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        registerStudent(workshopId, student);
        startWorkshop(workshopId);

        HttpResponse<String> response = checkIn(workshopId, student);

        assertThat(response.statusCode()).as("check-in non-verified: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body()).contains("not verified");
    }

    @Test
    void checkIn_duplicateScan_isIdempotentNoOp_noNewEntryNoResultChange() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKDUPE");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> first = checkIn(workshopId, student);
        assertThat(first.statusCode()).as("first check-in: %s", first.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        String recordId = readField(
                get("/api/v1/workshops/" + workshopId + "/attendance").body(), "recordId");

        HttpResponse<String> second = checkIn(workshopId, student);
        assertThat(second.statusCode()).as("second check-in: %s", second.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        // OQ-3B-3 idempotent no-op: same record, same result, no new MARK entry appended.
        assertThat(readField(get("/api/v1/workshops/" + workshopId + "/attendance").body(), "recordId"))
                .isEqualTo(recordId);

        HttpResponse<String> ledger = get("/api/v1/attendance-records/" + recordId);
        assertThat(ledger.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(countOccurrences(ledger.body(), "\"action\":\"MARK\"")).isEqualTo(1);
        assertThat(ledger.body()).contains("\"currentResult\":\"LATE\"");
    }

    @Test
    void checkIn_workshopNotInProgress_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKNOSTART");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);

        HttpResponse<String> response = checkIn(workshopId, student);

        assertThat(response.statusCode()).as("check-in not in progress: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void checkIn_trainerWithoutVerifiedSeat_returnsConflict() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKROLE");
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        startWorkshop(workshopId);

        // OQ-2: check-in maps any principal to STUDENT — eligibility is proven by ownership of a
        // verified seat. A trainer holds none, so the VERIFIED gate (OQ-14) rejects the scan.
        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/check-in",
                """
                {"qrReference": "QR-REF-%s"}
                """.formatted(UUID.randomUUID()),
                trainer.bearer());

        assertThat(response.statusCode()).as("check-in by trainer: %s", response.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body()).contains("not verified");
    }

    @Test
    void checkIn_blankQrReference_returnsBadRequest() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKBLANK");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> response = post("/api/v1/workshops/" + workshopId + "/attendance/check-in",
                """
                {"qrReference": "   "}
                """,
                student.bearer());

        assertThat(response.statusCode()).as("blank qr: %s", response.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void interaction_trainerCorrection_afterQrCheckIn_appendsTrainerMark() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKCORR");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> scanned = checkIn(workshopId, student);
        assertThat(scanned.statusCode()).as("check-in: %s", scanned.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        String recordId = readField(
                get("/api/v1/workshops/" + workshopId + "/attendance").body(), "recordId");

        // Trainer corrects the LATE scan to PRESENT — authoritative (append MARK, role TRAINER).
        mark(workshopId, student, trainer);

        HttpResponse<String> ledger = get("/api/v1/attendance-records/" + recordId);
        assertThat(ledger.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(countOccurrences(ledger.body(), "\"action\":\"MARK\"")).isEqualTo(2);
        assertThat(ledger.body()).contains("\"currentResult\":\"PRESENT\"");
        // The two MARK entries are ordered STUDENT (QR) then TRAINER (correction).
        int studentIndex = ledger.body().indexOf("\"actorRole\":\"STUDENT\"");
        int trainerIndex = ledger.body().indexOf("\"actorRole\":\"TRAINER\"");
        assertThat(studentIndex).isPositive();
        assertThat(trainerIndex).isGreaterThan(studentIndex);
    }

    @Test
    void interaction_afterWorkshopCompleted_checkInRejectedButAuditorAdjustWorks() throws Exception {
        UUID workshopId = createAndPublishWorkshop("CHKRECON");
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");
        IamE2eTestSupport.TestUser auditor = iam.registerAndLoginWithRoles("AUDITOR");
        registerAndVerify(workshopId, student, verifier);
        startWorkshop(workshopId);

        HttpResponse<String> scanned = checkIn(workshopId, student);
        assertThat(scanned.statusCode()).as("check-in: %s", scanned.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        String recordId = readField(
                get("/api/v1/workshops/" + workshopId + "/attendance").body(), "recordId");

        HttpResponse<String> completed = post("/api/v1/workshops/" + workshopId + "/complete", null, Map.of());
        assertThat(completed.statusCode()).as("complete: %s", completed.body()).isEqualTo(HttpStatus.NO_CONTENT.value());

        // State gate (ADR 0019 §3): workshop is COMPLETED → check-in is rejected (record no longer OPEN).
        HttpResponse<String> lateScan = checkIn(workshopId, student);
        assertThat(lateScan.statusCode()).as("check-in after complete: %s", lateScan.body())
                .isEqualTo(HttpStatus.CONFLICT.value());

        // Reconciliation is unaffected: auditor adjust still works in the RECONCILING window.
        HttpResponse<String> adjusted = post("/api/v1/attendance-records/" + recordId + "/adjust",
                """
                {"newStatus": "PRESENT", "reason": "CCTV confirms presence", "evidenceReference": "evidence://cam-1"}
                """,
                auditor.bearer());
        assertThat(adjusted.statusCode()).as("adjust: %s", adjusted.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}