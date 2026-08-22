package io.github.ryu200o.eduworkshop.room.internal.adapter.inbound.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import io.github.ryu200o.eduworkshop.shared.security.IamE2eTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomCommandControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient client = HttpClient.newHttpClient();
    private IamE2eTestSupport iam;
    private String facilityManagerToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        iam = new IamE2eTestSupport(port, client, objectMapper, jdbcTemplate);
        iam.seedAdmin(jdbcTemplate, passwordEncoder);
        facilityManagerToken = iam.registerAndLoginWithRoles("FACILITY_MANAGER").accessToken();
        userToken = iam.registerAndLogin().accessToken();
    }

    @Test
    void facilityManager_createsRoom_returns201() throws Exception {
        HttpResponse<String> response = createRoom(facilityManagerToken, "RBAC", 10, 1);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/api/v1/rooms/");
    }

    @Test
    void facilityManager_renamesRoom_returns204() throws Exception {
        UUID roomId = IamE2eTestSupport.idFromLocation(createRoom(facilityManagerToken, "RBAC", 10, 2));

        HttpResponse<String> response = request("PUT", "/api/v1/rooms/" + roomId + "/rename",
                "{\"newName\":\"RBAC-RENAMED\"}", facilityManagerToken);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    void user_cannotCreateRoom_returns403() throws Exception {
        HttpResponse<String> response = createRoom(userToken, "DENIED", 10, 3);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.body()).contains("\"code\":\"ACCESS_DENIED\"");
    }

    private HttpResponse<String> createRoom(String token, String building, int capacity, int code) throws Exception {
        return request("POST", "/api/v1/rooms",
                 """
                 {"building": "%s", "floor": 1, "code": %d, "name": "%s-%d-ROOM", "capacity": %d}
                 """.formatted(building, code, building, code, capacity), token);
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
