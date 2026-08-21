package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

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
 * End-to-end HTTP tests for the admin IAM surface (plan §1.1, ADR 0020 §2): create-user (OQ-4
 * ACTIVE + mcp), list/detail, update-roles, lock/unlock, disable/enable, reset-password, and the
 * admin-only authorization boundary. The bootstrap admin's password is re-seeded to a known value in
 * {@code @BeforeEach} (the seeded plaintext is unknown by design) and {@code must_change_password}
 * is lifted so the test can drive the whole admin surface.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamAdminControllerE2ETest {

    private static final String ADMIN_EMAIL = "admin@eduworkshop.local";
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanSchemaAndSeedAdmin() {
        jdbcTemplate.update("DELETE FROM iam_password_reset_tokens");
        jdbcTemplate.update("DELETE FROM iam_refresh_tokens");
        jdbcTemplate.update("DELETE FROM iam_user_roles WHERE user_id <> '" + ADMIN_ID + "'");
        jdbcTemplate.update("DELETE FROM iam_users WHERE email <> '" + ADMIN_EMAIL + "'");
        jdbcTemplate.update("UPDATE iam_users SET password_hash = ?, must_change_password = FALSE WHERE email = ?",
                passwordEncoder.encode(ADMIN_PASSWORD), ADMIN_EMAIL);
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

    private String adminAccessToken() throws Exception {
        HttpResponse<String> login = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        return json(login.body()).path("accessToken").asText();
    }

    private record CreatedUser(String userId, String email, String password) {
    }

    private CreatedUser createUser(String email, String password, String token) throws Exception {
        HttpResponse<String> create = request("POST", "/api/v1/iam/admin/users",
                "{\"email\":\"" + email + "\",\"fullName\":\"Nguyen Van C\","
                        + "\"temporaryPassword\":\"" + password + "\"}",
                token);
        assertThat(create.statusCode()).isEqualTo(201);
        String userId = IamE2eTestSupport.idFromLocation(create).toString();
        assertThat(userId).isNotBlank();
        return new CreatedUser(userId, email, password);
    }

    // ====================== TESTS ======================

    @Test
    void adminCreateUser_accountIsActiveWithMcp_andCanSetOwnPassword() throws Exception {
        String token = adminAccessToken();
        String email = "new-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> login = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login.body());
        assertThat(loginBody.path("mustChangePassword").asBoolean()).isTrue();
        String userToken = loginBody.path("accessToken").asText();

        // mcp gate: everything except change-password + logout is blocked.
        HttpResponse<String> blocked = request("GET", "/api/v1/iam/me", null, userToken);
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(json(blocked.body()).path("code").asText()).isEqualTo("MUST_CHANGE_PASSWORD_FIRST");

        HttpResponse<String> change = request("POST", "/api/v1/iam/me/change-password",
                "{\"currentPassword\":\"TempPassw0rd!\",\"newPassword\":\"MyOwnPass!1\"}", userToken);
        assertThat(change.statusCode()).isEqualTo(204);

        HttpResponse<String> newLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"MyOwnPass!1\"}", null);
        assertThat(newLogin.statusCode()).isEqualTo(200);
        assertThat(json(newLogin.body()).path("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void adminCreateUser_duplicateEmail_returns409() throws Exception {
        String token = adminAccessToken();
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> again = request("POST", "/api/v1/iam/admin/users",
                "{\"email\":\"" + email + "\",\"fullName\":\"Other\",\"temporaryPassword\":\"TempPassw0rd!\"}",
                token);
        assertThat(again.statusCode()).isEqualTo(409);
    }

    @Test
    void adminListUsers_containsCreatedUser() throws Exception {
        String token = adminAccessToken();
        String email = "listed-" + UUID.randomUUID() + "@example.com";
        createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> list = request("GET", "/api/v1/iam/admin/users", null, token);
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode users = json(list.body());
        assertThat(users.isArray()).isTrue();
        boolean found = false;
        for (JsonNode node : users) {
            if (node.path("email").asText().equals(email)) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void adminGetUserDetail_returnsSecurityProfile() throws Exception {
        String token = adminAccessToken();
        String email = "detail-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> detail = request("GET", "/api/v1/iam/admin/users/" + user.userId(), null, token);
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode body = json(detail.body());
        assertThat(body.path("email").asText()).isEqualTo(email);
        assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("mustChangePassword").asBoolean()).isTrue();
        assertThat(body.path("roles").toString()).contains("USER");

        HttpResponse<String> missing = request("GET", "/api/v1/iam/admin/users/"
                + UUID.randomUUID(), null, token);
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    @Test
    void adminUpdateRoles_appliesRoleSet() throws Exception {
        String token = adminAccessToken();
        String email = "roles-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> update = request("PUT", "/api/v1/iam/admin/users/" + user.userId() + "/roles",
                "{\"roles\":[\"USER\",\"PLANNER\"]}", token);
        assertThat(update.statusCode()).isEqualTo(204);

        HttpResponse<String> detail = request("GET", "/api/v1/iam/admin/users/" + user.userId(), null, token);
        assertThat(json(detail.body()).path("roles").toString()).contains("PLANNER");
    }

    @Test
    void adminLock_revokesSessions_andBlocksLogin_untilUnlock() throws Exception {
        String token = adminAccessToken();
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> login = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        String refreshToken = json(login.body()).path("refreshToken").asText();

        HttpResponse<String> lock = request("POST", "/api/v1/iam/admin/users/" + user.userId() + "/lock",
                null, token);
        assertThat(lock.statusCode()).isEqualTo(204);

        HttpResponse<String> refresh = request("POST", "/api/v1/iam/auth/refresh",
                "{\"refreshToken\":\"" + refreshToken + "\"}", null);
        assertThat(refresh.statusCode()).isEqualTo(401);

        HttpResponse<String> blockedLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(blockedLogin.statusCode()).isEqualTo(403);

        HttpResponse<String> unlock = request("POST", "/api/v1/iam/admin/users/" + user.userId() + "/unlock",
                null, token);
        assertThat(unlock.statusCode()).isEqualTo(204);

        HttpResponse<String> restoredLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(restoredLogin.statusCode()).isEqualTo(200);
    }

    @Test
    void adminDisable_blocksLogin_untilEnable() throws Exception {
        String token = adminAccessToken();
        String email = "disable-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> disable = request("POST", "/api/v1/iam/admin/users/" + user.userId() + "/disable",
                null, token);
        assertThat(disable.statusCode()).isEqualTo(204);

        HttpResponse<String> blockedLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(blockedLogin.statusCode()).isEqualTo(403);

        HttpResponse<String> enable = request("POST", "/api/v1/iam/admin/users/" + user.userId() + "/enable",
                null, token);
        assertThat(enable.statusCode()).isEqualTo(204);

        HttpResponse<String> restoredLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(restoredLogin.statusCode()).isEqualTo(200);
    }

    @Test
    void adminResetPassword_oldPasswordStopsWorking_andForcesMcp() throws Exception {
        String token = adminAccessToken();
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        CreatedUser user = createUser(email, "TempPassw0rd!", token);

        HttpResponse<String> reset = request("POST", "/api/v1/iam/admin/users/" + user.userId() + "/reset-password",
                "{\"newPassword\":\"AdminReset!1\"}", token);
        assertThat(reset.statusCode()).isEqualTo(204);

        HttpResponse<String> oldLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"TempPassw0rd!\"}", null);
        assertThat(oldLogin.statusCode()).isEqualTo(401);

        HttpResponse<String> newLogin = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"AdminReset!1\"}", null);
        assertThat(newLogin.statusCode()).isEqualTo(200);
        assertThat(json(newLogin.body()).path("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void nonAdminUser_cannotAccessAdminSurface() throws Exception {
        String email = "plain-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> register = request("POST", "/api/v1/iam/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"fullName\":\"Plain User\"}", null);
        assertThat(register.statusCode()).isEqualTo(201);
        UUID userId = IamE2eTestSupport.idFromLocation(register);
        String verifyToken = "verify-" + email;
        jdbcTemplate.update("""
                INSERT INTO iam_password_reset_tokens (id, user_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(), userId, sha256Hex(verifyToken), java.time.Instant.now().plusSeconds(3600));
        request("POST", "/api/v1/iam/auth/verify-email", "{\"token\":\"" + verifyToken + "\"}", null);

        HttpResponse<String> login = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}", null);
        String userToken = json(login.body()).path("accessToken").asText();

        HttpResponse<String> list = request("GET", "/api/v1/iam/admin/users", null, userToken);
        assertThat(list.statusCode()).isEqualTo(403);

        HttpResponse<String> create = request("POST", "/api/v1/iam/admin/users",
                "{\"email\":\"nope@example.com\",\"fullName\":\"Nope\",\"temporaryPassword\":\"X\"}",
                userToken);
        assertThat(create.statusCode()).isEqualTo(403);
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
}