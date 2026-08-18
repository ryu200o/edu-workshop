package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP tests for the 6 public IAM auth APIs — full stack against a real embedded server
 * ({@code RANDOM_PORT}): HttpClient → {@link IamAuthController} → CommandBus → handlers → JPA
 * adapters → H2 (Flyway V21).
 *
 * <p>Unlike the business E2E tests, this class intentionally does NOT contribute a permit-all chain:
 * it exercises the real scoped {@code IamSecurityConfig} chain. The chain is scoped to
 * {@code /api/v1/iam/**}, so the 6 public auth endpoints are reached under their permitAll rule
 * while the JWT filter still runs for the mcp-gate scenario.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamAuthControllerE2ETest {

    private static final String ADMIN_EMAIL = "admin@eduworkshop.local";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM iam_password_reset_tokens");
        jdbcTemplate.update("DELETE FROM iam_refresh_tokens");
        jdbcTemplate.update("DELETE FROM iam_user_roles WHERE user_id <> '00000000-0000-0000-0000-000000000001'");
        jdbcTemplate.update("DELETE FROM iam_users WHERE email <> '" + ADMIN_EMAIL + "'");
    }

    // ====================== HELPER ======================

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private record RegisteredUser(String email, String password, String verifyToken, UUID userId) {
    }

    private RegisteredUser registerAndVerify() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";
        HttpResponse<String> register = post("/api/v1/iam/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Nguyen Van A\"}");
        assertThat(register.statusCode()).isEqualTo(201);
        JsonNode reg = json(register.body());
        String verifyToken = reg.path("verifyToken").asText();
        UUID userId = UUID.fromString(reg.path("userId").asText());

        HttpResponse<String> verify = post("/api/v1/iam/auth/verify-email",
                "{\"token\":\"" + verifyToken + "\"}");
        assertThat(verify.statusCode()).isEqualTo(200);
        return new RegisteredUser(email, password, verifyToken, userId);
    }

    // ====================== TESTS ======================

    @Test
    void register_verify_login_refresh_fullFlow() throws Exception {
        RegisteredUser user = registerAndVerify();

        HttpResponse<String> login = post("/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login.body());
        String accessToken = loginBody.path("accessToken").asText();
        String refreshToken = loginBody.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(loginBody.path("mustChangePassword").asBoolean()).isFalse();

        HttpResponse<String> refresh = post("/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + refreshToken + "\"}");
        assertThat(refresh.statusCode()).isEqualTo(200);
        JsonNode refreshBody = json(refresh.body());
        assertThat(refreshBody.path("accessToken").asText()).isNotBlank();
        assertThat(refreshBody.path("refreshToken").asText()).isNotBlank();

        // RTR: the consumed refresh token must no longer be usable.
        HttpResponse<String> reuse = post("/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + refreshToken + "\"}");
        assertThat(reuse.statusCode()).isEqualTo(401);
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisteredUser user = registerAndVerify();

        HttpResponse<String> again = post("/api/v1/iam/auth/register",
                "{\"email\":\"" + user.email() + "\",\"password\":\"Passw0rd!\",\"fullName\":\"Other\"}");
        assertThat(again.statusCode()).isEqualTo(409);
    }

    @Test
    void login_wrongPassword_fiveTimes_locksAccount() throws Exception {
        RegisteredUser user = registerAndVerify();

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> wrong = post("/api/v1/iam/auth/login",
                    "{\"email\":\"" + user.email() + "\",\"password\":\"WrongPass!\"}");
            assertThat(wrong.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> correct = post("/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}");
        assertThat(correct.statusCode()).isEqualTo(403);
    }

    @Test
    void forgotPassword_resetPassword_newCredentialsWork() throws Exception {
        RegisteredUser user = registerAndVerify();

        HttpResponse<String> forgot = post("/api/v1/iam/auth/forgot-password",
                "{\"email\":\"" + user.email() + "\"}");
        assertThat(forgot.statusCode()).isEqualTo(200);
        String resetToken = json(forgot.body()).path("resetToken").asText();
        assertThat(resetToken).isNotBlank();

        HttpResponse<String> reset = post("/api/v1/iam/auth/reset-password",
                "{\"token\":\"" + resetToken + "\",\"newPassword\":\"BrandNew!99\"}");
        assertThat(reset.statusCode()).isEqualTo(200);

        HttpResponse<String> oldLogin = post("/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}");
        assertThat(oldLogin.statusCode()).isEqualTo(401);

        HttpResponse<String> newLogin = post("/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"BrandNew!99\"}");
        assertThat(newLogin.statusCode()).isEqualTo(200);
    }

    @Test
    void forgotPassword_unknownEmail_isSilentlyOkWithoutToken() throws Exception {
        HttpResponse<String> forgot = post("/api/v1/iam/auth/forgot-password",
                "{\"email\":\"nobody@example.com\"}");
        assertThat(forgot.statusCode()).isEqualTo(200);
        assertThat(json(forgot.body()).path("resetToken").isNull()).isTrue();
    }

    @Test
    void verifyEmail_usedOrUnknownToken_returns401() throws Exception {
        RegisteredUser user = registerAndVerify();

        // replay the already-consumed verify token
        HttpResponse<String> replay = post("/api/v1/iam/auth/verify-email",
                "{\"token\":\"" + user.verifyToken() + "\"}");
        assertThat(replay.statusCode()).isEqualTo(401);

        HttpResponse<String> unknown = post("/api/v1/iam/auth/verify-email",
                "{\"token\":\"definitely-not-a-real-token\"}");
        assertThat(unknown.statusCode()).isEqualTo(401);
    }

    @Test
    void mustChangePassword_user_isBlockedFromNonWhitelistedApis() throws Exception {
        RegisteredUser user = registerAndVerify();

        // Simulate a temporary-password flow: force the mcp gate on, then log in.
        jdbcTemplate.update(
                "UPDATE iam_users SET must_change_password = TRUE WHERE email = ?", user.email());

        HttpResponse<String> login = post("/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login.body());
        assertThat(loginBody.path("mustChangePassword").asBoolean()).isTrue();
        String accessToken = loginBody.path("accessToken").asText();

        // The refresh endpoint is not in the mcp whitelist → 403 + business code.
        HttpResponse<String> blocked = post("/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + loginBody.path("refreshToken").asText() + "\"}",
                accessToken);
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(json(blocked.body()).path("code").asText()).isEqualTo("MUST_CHANGE_PASSWORD_FIRST");
    }
}
