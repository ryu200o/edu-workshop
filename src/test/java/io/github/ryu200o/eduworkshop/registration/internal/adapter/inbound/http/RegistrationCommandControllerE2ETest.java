package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

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
 * End-to-end HTTP test for the Registration write side, exercising the full stack against a real
 * embedded server ({@code RANDOM_PORT}): HttpClient → {@link RegistrationCommandController} →
 * shared CommandBus → Application handlers → JPA/JOOQ adapters → H2 (Flyway). A published workshop
 * is seeded through the Workshop/Room HTTP APIs (real use-case orchestration, not mocks).
 *
 * <p>Learners and verifiers are real IAM users carrying Bearer tokens (register → login → Bearer,
 * plan §7 Slice 5); the acting user always comes from the authenticated principal and only a
 * {@code VERIFIER} global role passes the verify gate (OQ-3C-1). The removed permit-all test chain
 * is gone.</p>
 *
 * <p>The workshop lifecycle scheduler is disabled ({@code app.workshop.lifecycle.enabled=false})
 * so a published workshop whose start time has already passed is not auto-started mid-test
 * (mirroring {@code AttendanceCommandControllerE2ETest}); {@code verify_allowsWorkshopInProgress}
 * deliberately publishes a past-start workshop and must reach {@code register} while still
 * {@code PUBLISHED}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.workshop.lifecycle.enabled=false"})
class RegistrationCommandControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private static final Instant START = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(2));

    private IamE2eTestSupport iam;
    private String operatorBearer;
    private String facilityManagerBearer;

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLogin().accessToken();
        facilityManagerBearer = iam.registerAndLoginWithRoles("FACILITY_MANAGER").accessToken();
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

    private UUID createRoom(String building) throws Exception {
        HttpResponse<String> response = post("/api/v1/rooms",
                """
                {"building": "%s", "floor": 1, "code": 1, "name": "%s-ROOM-1", "capacity": 50}
                """.formatted(building, building),
                Map.of("Authorization", "Bearer " + facilityManagerBearer));
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
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
        return IamE2eTestSupport.idFromLocation(response);
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
        assertThat(planned.statusCode()).as("plan workshop: %s", planned.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish workshop: %s", published.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private HttpResponse<String> register(UUID workshopId, IamE2eTestSupport.TestUser student) throws Exception {
        return post("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId), student.bearer());
    }

    private static String readField(HttpResponse<String> response, String field) {
        String body = response.body();
        int start = body.indexOf("\"" + field + "\"");
        int colon = body.indexOf(":", start);
        int begin = body.indexOf("\"", colon) + 1;
        return body.substring(begin, body.indexOf("\"", begin));
    }

    // Commands are void (Strict CQS / ADR 0021): the register/cancel/verify responses carry no body,
    // so the registration id and state changes are re-derived from the write-side table.
    private UUID findRegistrationId(UUID workshopId, IamE2eTestSupport.TestUser student) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM registrations WHERE workshop_id = ? AND user_id = ?",
                UUID.class, workshopId, student.userId());
    }

    private String readStatus(UUID registrationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM registrations WHERE id = ?", String.class, registrationId);
    }

    private String readVerifiedAtRaw(UUID registrationId) {
        return jdbcTemplate.queryForObject(
                "SELECT verified_at FROM registrations WHERE id = ?", String.class, registrationId);
    }

    @Test
    void registerAndCancel_happyPath() throws Exception {
        UUID roomId = createRoom("HAPPY");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();

        HttpResponse<String> registered = register(workshopId, student);
        assertThat(registered.statusCode()).as("register: %s", registered.body()).isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = findRegistrationId(workshopId, student);

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                student.bearer());
        assertThat(cancelled.statusCode()).as("cancel: %s", cancelled.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void register_duplicateActiveRegistration_returnsConflict() throws Exception {
        UUID roomId = createRoom("DUP");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();

        assertThat(register(workshopId, student).statusCode()).isEqualTo(HttpStatus.CREATED.value());
        HttpResponse<String> duplicate = register(workshopId, student);
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
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> registered = register(workshopId, iam.registerAndLogin());
        assertThat(registered.statusCode()).as("register draft: %s", registered.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void register_whenCapacityFull_returnsConflict() throws Exception {
        UUID roomId = createRoom("FULL");
        UUID workshopId = publishWorkshop(roomId, 1);

        IamE2eTestSupport.TestUser firstStudent = iam.registerAndLogin();
        assertThat(register(workshopId, firstStudent).statusCode()).as("first seat: register")
                .isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> second = register(workshopId, iam.registerAndLogin());
        assertThat(second.statusCode()).as("second seat beyond capacity").isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(second.body()).contains("capacity=1");
    }

    @Test
    void unauthenticatedRegister_returnsUnauthorized() throws Exception {
        UUID roomId = createRoom("NOUSER");
        UUID workshopId = publishWorkshop(roomId);

        HttpResponse<String> response = postUnauthenticated("/api/v1/registrations",
                """
                {"workshopId": "%s"}
                """.formatted(workshopId));
        assertThat(response.statusCode()).as("register without token: %s", response.body())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private HttpResponse<String> postUnauthenticated(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void cancel_notOwner_returnsForbidden() throws Exception {
        UUID roomId = createRoom("OWNER");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser owner = iam.registerAndLogin();
        IamE2eTestSupport.TestUser stranger = iam.registerAndLogin();

        HttpResponse<String> registered = register(workshopId, owner);
        UUID registrationId = findRegistrationId(workshopId, owner);

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                stranger.bearer());
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
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish: %s", published.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();

        HttpResponse<String> registered = register(workshopId, student); // starts within 24h → past deadline
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = findRegistrationId(workshopId, student);

        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                student.bearer());
        assertThat(cancelled.statusCode()).as("cancel past deadline: %s", cancelled.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // ----------------------------------------------------------------
    // verify (Epic 3C — REGISTERED → VERIFIED via thin QR seam)
    // ----------------------------------------------------------------

    private HttpResponse<String> verify(String qrReference, Map<String, String> headers) throws Exception {
        return post("/api/v1/registrations/verify",
                """
                {"qrReference": "%s"}
                """.formatted(qrReference), headers);
    }

    private HttpResponse<String> startWorkshop(UUID workshopId) throws Exception {
        return post("/api/v1/workshops/" + workshopId + "/start", null, Map.of());
    }

    @Test
    void verify_verifierHappyPath_marksSeatVerified() throws Exception {
        UUID roomId = createRoom("VERIFY-OK");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");

        HttpResponse<String> registered = register(workshopId, student);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = findRegistrationId(workshopId, student);

        HttpResponse<String> verified = verify("QR-REG-" + registrationId, verifier.bearer());
        assertThat(verified.statusCode()).as("verify: %s", verified.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(readStatus(registrationId)).isEqualTo("VERIFIED");
    }

    @Test
    void verify_allowsWorkshopInProgress() throws Exception {
        UUID roomId = createRoom("VERIFY-INPROG");
        Instant pastStart = Instant.now().minus(Duration.ofHours(1));
        UUID workshopId = createWorkshop("WS-" + UUID.randomUUID(), pastStart, pastStart.plus(Duration.ofHours(2)));
        HttpResponse<String> planned = post("/api/v1/workshops/" + workshopId + "/plan",
                """
                {"roomId": "%s"}
                """.formatted(roomId), Map.of());
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish: %s", published.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");

        HttpResponse<String> registered = register(workshopId, student);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = findRegistrationId(workshopId, student);

        assertThat(startWorkshop(workshopId).statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId, verifier.bearer());
        assertThat(verified.statusCode()).as("verify IN_PROGRESS: %s", verified.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void verify_idempotent_whenAlreadyVerified() throws Exception {
        UUID roomId = createRoom("VERIFY-IDEM");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");

        HttpResponse<String> registered = register(workshopId, student);
        UUID registrationId = findRegistrationId(workshopId, student);
        Map<String, String> headers = verifier.bearer();

        HttpResponse<String> first = verify("QR-REG-" + registrationId, headers);
        assertThat(first.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        String firstVerifiedAt = readVerifiedAtRaw(registrationId);

        HttpResponse<String> second = verify("QR-REG-" + registrationId, headers);
        assertThat(second.statusCode()).as("re-verify: %s", second.body()).isEqualTo(HttpStatus.NO_CONTENT.value());
        // Re-verify must not advance verifiedAt: compare the raw stored value before/after the
        // second verify (H2/PostgreSQL TIMESTAMPTZ) — identical means the idempotent no-op held.
        assertThat(readVerifiedAtRaw(registrationId)).isEqualTo(firstVerifiedAt);
    }

    @Test
    void verify_nonVerifierRole_returnsForbidden() throws Exception {
        UUID roomId = createRoom("VERIFY-ROLE");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser trainer = iam.registerAndLoginWithRoles("PLANNER");

        HttpResponse<String> registered = register(workshopId, student);
        UUID registrationId = findRegistrationId(workshopId, student);

        HttpResponse<String> verified = verify("QR-REG-" + registrationId, trainer.bearer());
        assertThat(verified.statusCode()).as("non-verifier: %s", verified.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void verify_unknownQrReference_returnsNotFound() throws Exception {
        HttpResponse<String> verified = verify("QR-REG-" + UUID.randomUUID(),
                iam.registerAndLoginWithRoles("VERIFIER").bearer());
        assertThat(verified.statusCode()).as("unknown QR: %s", verified.body())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void verify_cancelledWorkshop_returnsConflict() throws Exception {
        UUID roomId = createRoom("VERIFY-WSX");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();

        HttpResponse<String> registered = register(workshopId, student);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = findRegistrationId(workshopId, student);

        HttpResponse<String> cancelled = post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of());
        assertThat(cancelled.statusCode()).as("cancel workshop: %s", cancelled.body())
                .isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                iam.registerAndLoginWithRoles("VERIFIER").bearer());
        assertThat(verified.statusCode()).as("verify cancelled workshop: %s", verified.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void verify_cancelledRegistration_returnsConflict() throws Exception {
        UUID roomId = createRoom("VERIFY-CANCEL");
        UUID workshopId = publishWorkshop(roomId);
        IamE2eTestSupport.TestUser student = iam.registerAndLogin();
        IamE2eTestSupport.TestUser verifier = iam.registerAndLoginWithRoles("VERIFIER");

        HttpResponse<String> registered = register(workshopId, student);
        UUID registrationId = findRegistrationId(workshopId, student);
        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                student.bearer());
        assertThat(cancelled.statusCode()).as("cancel: %s", cancelled.body()).isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId, verifier.bearer());
        assertThat(verified.statusCode()).as("verify cancelled: %s", verified.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }
}