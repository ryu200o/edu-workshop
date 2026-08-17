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
import static org.assertj.core.api.Assertions.within;

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
        UUID studentId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", verifierId.toString()));
        assertThat(verified.statusCode()).as("verify: %s", verified.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(verified.body()).contains("\"registrationId\":\"" + registrationId + "\"");
        assertThat(readField(verified, "verifiedAt")).isNotBlank();
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
        assertThat(planned.statusCode()).as("plan: %s", planned.body()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> published = post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of());
        assertThat(published.statusCode()).as("publish: %s", published.body()).isEqualTo(HttpStatus.OK.value());
        UUID studentId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        assertThat(startWorkshop(workshopId).statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", verifierId.toString()));
        assertThat(verified.statusCode()).as("verify IN_PROGRESS: %s", verified.body())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void verify_idempotent_whenAlreadyVerified() throws Exception {
        UUID roomId = createRoom("VERIFY-IDEM");
        UUID workshopId = publishWorkshop(roomId);
        UUID studentId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));
        Map<String, String> headers = Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", verifierId.toString());

        HttpResponse<String> first = verify("QR-REG-" + registrationId, headers);
        assertThat(first.statusCode()).isEqualTo(HttpStatus.OK.value());
        String firstVerifiedAt = readField(first, "verifiedAt");

        HttpResponse<String> second = verify("QR-REG-" + registrationId, headers);
        assertThat(second.statusCode()).as("re-verify: %s", second.body()).isEqualTo(HttpStatus.OK.value());
        // Re-verify must not advance verifiedAt: the first response is read from the in-memory
        // aggregate (nanos), the second is re-loaded from the DB (rounded to micros) — compare within
        // a 1µs tolerance to absorb the storage rounding (H2/PostgreSQL TIMESTAMPTZ).
        assertThat(Instant.parse(readField(second, "verifiedAt")))
                .isCloseTo(Instant.parse(firstVerifiedAt), within(Duration.ofNanos(1000)));
    }

    @Test
    void verify_nonVerifierRole_returnsForbidden() throws Exception {
        UUID roomId = createRoom("VERIFY-ROLE");
        UUID workshopId = publishWorkshop(roomId);
        UUID studentId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-Actor-Role", "TRAINER", "X-User-Id", UUID.randomUUID().toString()));
        assertThat(verified.statusCode()).as("non-verifier: %s", verified.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void verify_unknownQrReference_returnsNotFound() throws Exception {
        HttpResponse<String> verified = verify("QR-REG-" + UUID.randomUUID(),
                Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", UUID.randomUUID().toString()));
        assertThat(verified.statusCode()).as("unknown QR: %s", verified.body())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void verify_cancelledWorkshop_returnsConflict() throws Exception {
        UUID roomId = createRoom("VERIFY-WSX");
        UUID workshopId = publishWorkshop(roomId);
        UUID studentId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        assertThat(registered.statusCode()).as("register: %s", registered.body())
                .isEqualTo(HttpStatus.CREATED.value());
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> cancelled = post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of());
        assertThat(cancelled.statusCode()).as("cancel workshop: %s", cancelled.body())
                .isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", UUID.randomUUID().toString()));
        assertThat(verified.statusCode()).as("verify cancelled workshop: %s", verified.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void verify_cancelledRegistration_returnsConflict() throws Exception {
        UUID roomId = createRoom("VERIFY-CANCEL");
        UUID workshopId = publishWorkshop(roomId);
        UUID studentId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));
        HttpResponse<String> cancelled = post("/api/v1/registrations/" + registrationId + "/cancel", null,
                Map.of("X-User-Id", studentId.toString()));
        assertThat(cancelled.statusCode()).as("cancel: %s", cancelled.body()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-Actor-Role", "VERIFIER", "X-User-Id", UUID.randomUUID().toString()));
        assertThat(verified.statusCode()).as("verify cancelled: %s", verified.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void verify_missingRoleHeader_returnsBadRequest() throws Exception {
        UUID roomId = createRoom("VERIFY-HDR");
        UUID workshopId = publishWorkshop(roomId);
        UUID studentId = UUID.randomUUID();

        HttpResponse<String> registered = register(workshopId, studentId);
        UUID registrationId = UUID.fromString(readField(registered, "registrationId"));

        HttpResponse<String> verified = verify("QR-REG-" + registrationId,
                Map.of("X-User-Id", UUID.randomUUID().toString()));
        assertThat(verified.statusCode()).as("missing role header: %s", verified.body())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
