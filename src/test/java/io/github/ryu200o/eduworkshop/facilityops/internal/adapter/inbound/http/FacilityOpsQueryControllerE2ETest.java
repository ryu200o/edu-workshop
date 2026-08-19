package io.github.ryu200o.eduworkshop.facilityops.internal.adapter.inbound.http;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP test for the FacilityOps read side ({@code RANDOM_PORT}): HttpClient →
 * {@link FacilityOpsQueryController} → shared QueryBus → FacilityOps handler → public
 * {@code *ExposeAPI} of the Room/Workshop/Registration modules. Verifies the 200 OK contract and the
 * 404 NOT_FOUND (RFC 9457 {@code application/problem+json}) for an unknown room.
 *
 * <p>Calls are authenticated through the real IAM flow (register → login → Bearer, plan §7 Slice 5);
 * the removed permit-all test chain is gone.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FacilityOpsQueryControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private IamE2eTestSupport iam;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        bearer = iam.registerAndLogin().accessToken();
    }

    @Test
    void previewImpact_existingRoom_returns200WithView() throws Exception {
        HttpResponse<String> created = createRoom(Map.of(
                "building", "OPS", "floor", 3, "code", 11, "name", "OPS-ROOM-11", "capacity", 40));
        assertThat(created.statusCode()).as("room: %s", created.body()).isEqualTo(HttpStatus.OK.value());
        String roomId = extractId(created.body());

        HttpResponse<String> response = previewImpact(roomId, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");

        assertThat(response.statusCode()).as("preview: %s", response.body()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body())
                .contains("\"roomId\":")
                .contains("\"publishedWorkshopsCount\":0")
                .contains("\"plannedWorkshopsCount\":0")
                .contains("\"totalAffectedStudentsCount\":0");
    }

    @Test
    void previewImpact_unknownRoom_returns404ProblemDetail() throws Exception {
        HttpResponse<String> response = previewImpact(UUID.randomUUID().toString(),
                "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");

        assertThat(response.statusCode()).as("preview: %s", response.body())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .as("content-type: %s", response.body())
                .contains("application/problem+json");
        assertThat(response.body()).contains("\"status\":404").contains("\"detail\":");
    }

    private HttpResponse<String> createRoom(Map<String, Object> room) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/rooms"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"building\": \"%s\", \"floor\": %d, \"code\": %d, \"name\": \"%s\", \"capacity\": %d}"
                                .formatted(room.get("building"), room.get("floor"), room.get("code"),
                                        room.get("name"), room.get("capacity"))))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> previewImpact(String roomId, String startTime, String endTime) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/v1/facility-ops/rooms/" + roomId
                                + "/maintenance-impact-preview?startTime=" + startTime + "&endTime=" + endTime))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String extractId(String body) {
        return body.replaceAll(".*\\\"id\\\"\\s*:\\s*\\\"([^\"]+)\\\".*", "$1");
    }
}