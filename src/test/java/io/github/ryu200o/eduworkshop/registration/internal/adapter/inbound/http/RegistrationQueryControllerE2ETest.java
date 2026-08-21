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
 * End-to-end HTTP test for the Registration read side (learner "My Bookings"), exercising the full
 * stack with a real embedded server: HttpClient → {@link RegistrationQueryController} → shared
 * QueryBus → {@code GetMyRegistrationsQueryHandler} → JOOQ adapter → H2 (Flyway). A published
 * workshop is seeded through the real Workshop/Room HTTP APIs, then registered, and the GET response
 * is asserted to carry the snapshotted title / room / start / end (ADR 0007 single-table pushdown).
 *
 * <p>Learners are real IAM users (register → login → Bearer, plan §7 Slice 5); the removed permit-all
 * test chain is gone. The acting user always comes from the authenticated principal.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationQueryControllerE2ETest {

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

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        operatorBearer = iam.registerAndLogin().accessToken();
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

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        withAuth(headers).forEach(builder::header);
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
                """.formatted(building, building), Map.of());
        assertThat(response.statusCode()).as("create room: %s", response.body()).isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
    }

    private UUID publishWorkshop(UUID roomId, String building) throws Exception {
        UUID workshopId = createWorkshop(building, START, END);
        assertThat(post("/api/v1/workshops/" + workshopId + "/plan",
                "{\"roomId\":\"%s\"}".formatted(roomId), Map.of()).statusCode())
        .isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(post("/api/v1/workshops/" + workshopId + "/publish", null, Map.of()).statusCode())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        return workshopId;
    }

    private UUID createWorkshop(String title, Instant start, Instant end) throws Exception {
        HttpResponse<String> response = post("/api/v1/workshops",
                """
                {"title": "%s", "description": "My Bookings E2E", "startTime": "%s", "endTime": "%s", "capacity": 30}
                """.formatted(title, start, end), Map.of());
        assertThat(response.statusCode()).as("create workshop: %s", response.body())
                .isEqualTo(HttpStatus.CREATED.value());
        return IamE2eTestSupport.idFromLocation(response);
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
        IamE2eTestSupport.TestUser user = iam.registerAndLogin();

        HttpResponse<String> registered = post("/api/v1/registrations",
                "{\"workshopId\":\"%s\"}".formatted(workshopId), user.bearer());
        assertThat(registered.statusCode()).as("register: %s", registered.body()).isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> response = get("/api/v1/registrations", user.bearer());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        String body = response.body();
        assertThat(body).contains("WS-MYBK");                  // workshopTitle snapshot
        assertThat(body).contains("MYBK-ROOM-1");              // room name snapshot (seeded by plan)
        assertThat(body).contains(START.toString());           // workshopStartTime snapshot
        assertThat(body).contains(END.toString());             // workshopEndTime snapshot
        assertThat(body).contains("REGISTERED");               // status
        assertThat(body).contains(user.userId().toString());   // userId
    }

    @Test
    void myBookings_statusFilter_restrictsRows() throws Exception {
        UUID roomId = createRoom("STFLT");
        UUID workshopId = publishWorkshop(roomId, "WS-STFLT");
        IamE2eTestSupport.TestUser user = iam.registerAndLogin();

        assertThat(post("/api/v1/registrations", "{\"workshopId\":\"%s\"}".formatted(workshopId),
                user.bearer()).statusCode()).isEqualTo(HttpStatus.CREATED.value());

        HttpResponse<String> registeredOnly = get("/api/v1/registrations?status=REGISTERED", user.bearer());
        assertThat(registeredOnly.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(registeredOnly.body()).contains("REGISTERED");
        assertThat(registeredOnly.body()).doesNotContain("REFUNDED");

        // REFUNDED is a learner-selectable filter: cancel the workshop to flip the seat to REFUNDED,
        // then the status=REFUNDED query must return that row (e-commerce style refunded-order view).
        assertThat(post("/api/v1/workshops/" + workshopId + "/cancel", null, Map.of()).statusCode())
        .isEqualTo(HttpStatus.NO_CONTENT.value());

        HttpResponse<String> refunded = get("/api/v1/registrations?status=REFUNDED", user.bearer());
        assertThat(refunded.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(refunded.body()).contains("REFUNDED");

        HttpResponse<String> registeredAfter = get("/api/v1/registrations?status=REGISTERED", user.bearer());
        assertThat(registeredAfter.body()).doesNotContain("REFUNDED");
    }

    @Test
    void myBookings_learnerWithoutBookings_returnsEmptyList() throws Exception {
        UUID roomId = createRoom("NOBK");
        publishWorkshop(roomId, "WS-NOBK");

        HttpResponse<String> response = get("/api/v1/registrations", iam.registerAndLogin().bearer());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isEqualTo("[]");
    }
}