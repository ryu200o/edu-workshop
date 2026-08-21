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

import io.github.ryu200o.eduworkshop.shared.security.IamE2eTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP tests for the IAM self-service surface (plan §1.1, ADR 0020 §2): {@code GET /me},
 * {@code PUT /me/profile} (OQ-5 — email/password banned), {@code POST /me/change-password},
 * {@code POST /auth/logout} and {@code POST /me/logout-all}. Full stack against a real embedded
 * server, exercising the real scoped {@code IamSecurityConfig} chain (authenticated rule on
 * {@code /api/v1/iam/me/**}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamSelfControllerE2ETest {

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

    // ====================== HELPERS ======================

    private HttpResponse<String> request(String method, String path, String body, String bearer)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private record RegisteredUser(String email, String password, String refreshToken, String accessToken) {
    }

    private RegisteredUser registerVerifyAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";

        HttpResponse<String> register = request("POST", "/api/v1/iam/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Nguyen Van A\"}",
                null);
        assertThat(register.statusCode()).isEqualTo(201);
        UUID userId = IamE2eTestSupport.idFromLocation(register);
        String verifyToken = "verify-" + email;
        seedToken(userId, verifyToken);

        HttpResponse<String> verify = request("POST", "/api/v1/iam/auth/verify-email",
                "{\"token\":\"" + verifyToken + "\"}", null);
        assertThat(verify.statusCode()).isEqualTo(204);

        HttpResponse<String> login = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login.body());
        return new RegisteredUser(email, password,
                loginBody.path("refreshToken").asText(), loginBody.path("accessToken").asText());
    }

    /** Test seam: inserts a known raw token + its SHA-256 digest (plan §2.2). */
    private void seedToken(UUID userId, String rawToken) {
        jdbcTemplate.update("""
                INSERT INTO iam_password_reset_tokens (id, user_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(), userId, sha256Hex(rawToken), java.time.Instant.now().plusSeconds(3600));
    }

    private static String sha256Hex(String rawToken) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    // ====================== TESTS ======================

    @Test
    void getMe_authenticated_returnsOwnProfile() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> response = request("GET", "/api/v1/iam/me", null, user.accessToken());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response.body());
        assertThat(body.path("email").asText()).isEqualTo(user.email());
        assertThat(body.path("fullName").asText()).isEqualTo("Nguyen Van A");
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("roles").get(0).asText()).isEqualTo("USER");
        assertThat(body.path("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void getMe_unauthenticated_returns403() throws Exception {
        HttpResponse<String> response = request("GET", "/api/v1/iam/me", null, null);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void updateProfile_success_reflectsNewValues() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> update = request("PUT", "/api/v1/iam/me/profile",
                "{\"fullName\":\"Tran Thi B\",\"phoneNumber\":\"0901234567\","
                        + "\"studentCode\":\"B21DCVT000\",\"avatarUrl\":\"https://cdn.example.com/a.png\"}",
                user.accessToken());
        assertThat(update.statusCode()).isEqualTo(204);

        HttpResponse<String> reload = request("GET", "/api/v1/iam/me", null, user.accessToken());
        assertThat(json(reload.body()).path("fullName").asText()).isEqualTo("Tran Thi B");
    }

    @Test
    void updateProfile_sendingEmailOrPassword_isRejected() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> withEmail = request("PUT", "/api/v1/iam/me/profile",
                "{\"fullName\":\"X\",\"email\":\"hacked@example.com\"}", user.accessToken());
        assertThat(withEmail.statusCode()).isEqualTo(400);

        HttpResponse<String> withPassword = request("PUT", "/api/v1/iam/me/profile",
                "{\"fullName\":\"X\",\"password\":\"Hacked!1\"}", user.accessToken());
        assertThat(withPassword.statusCode()).isEqualTo(400);
    }

    @Test
    void changePassword_success_oldPasswordStopsWorking_andSessionsDie() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> change = request("POST", "/api/v1/iam/me/change-password",
                "{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"BrandNew!99\"}", user.accessToken());
        assertThat(change.statusCode()).isEqualTo(204);

        HttpResponse<String> oldLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"Passw0rd!\"}", null);
        assertThat(oldLogin.statusCode()).isEqualTo(401);

        HttpResponse<String> newLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + user.email() + "\",\"password\":\"BrandNew!99\"}", null);
        assertThat(newLogin.statusCode()).isEqualTo(200);
        assertThat(json(newLogin.body()).path("mustChangePassword").asBoolean()).isFalse();

        HttpResponse<String> reuse = request("POST", "/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + user.refreshToken() + "\"}", null);
        assertThat(reuse.statusCode()).isEqualTo(401);
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> change = request("POST", "/api/v1/iam/me/change-password",
                "{\"currentPassword\":\"WrongPass!\",\"newPassword\":\"BrandNew!99\"}", user.accessToken());
        assertThat(change.statusCode()).isEqualTo(401);
    }

    @Test
    void logout_revokesThePresentedRefreshToken() throws Exception {
        RegisteredUser user = registerVerifyAndLogin();

        HttpResponse<String> logout = request("POST", "/api/v1/iam/auth/logout",
                "{\"refreshToken\":\"" + user.refreshToken() + "\"}", null);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> refresh = request("POST", "/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + user.refreshToken() + "\"}", null);
        assertThat(refresh.statusCode()).isEqualTo(401);
    }

    @Test
    void logoutAll_revokesEveryActiveSession() throws Exception {
        RegisteredUser first = registerVerifyAndLogin();
        HttpResponse<String> secondLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + first.email() + "\",\"password\":\"" + first.password() + "\"}", null);
        String secondRefresh = json(secondLogin.body()).path("refreshToken").asText();

        HttpResponse<String> logoutAll = request("POST", "/api/v1/iam/me/logout-all", "{}",
                first.accessToken());
        assertThat(logoutAll.statusCode()).isEqualTo(204);

        HttpResponse<String> reuseFirst = request("POST", "/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + first.refreshToken() + "\"}", null);
        assertThat(reuseFirst.statusCode()).isEqualTo(401);

        HttpResponse<String> reuseSecond = request("POST", "/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + secondRefresh + "\"}", null);
        assertThat(reuseSecond.statusCode()).isEqualTo(401);
    }
}