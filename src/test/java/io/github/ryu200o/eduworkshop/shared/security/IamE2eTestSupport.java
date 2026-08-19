package io.github.ryu200o.eduworkshop.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared-test HTTP support (plan §7 Slice 5 / §9) for full-stack E2E tests against a real embedded
 * server ({@code RANDOM_PORT}). Drives the real IAM auth surface over HTTP: register → verify-email →
 * (grant global roles via the admin API) → login → {@code Bearer} access token — the production
 * identity flow business E2E tests must now use instead of the removed {@code X-User-Id}/
 * {@code X-Actor-Role} headers / permit-all chains.
 *
 * <p>The bootstrap admin password is unknown by design; {@link #seedAdmin} re-seeds it to a known
 * value (via SQL, mirroring {@code IamAdminControllerE2ETest}) so tests can mint an admin token for
 * role grants. Public {@code /auth/register} creates accounts with {@code USER} role and
 * {@code must_change_password = false}, so self-registered users can immediately use business APIs.</p>
 *
 * <p>The register/forgot-password endpoints are strictly-void (201/204, no body), so the raw verify /
 * reset token is never returned over HTTP (plan §2.2). The test seam therefore self-seeds the token:
 * it looks up the persisted user by email via {@link JdbcTemplate} and inserts a known raw token +
 * its SHA-256 digest directly into {@code iam_password_reset_tokens}, mirroring what the handlers
 * would persist. Unit tests cover the handler-side generation/hashing; this support only consumes
 * tokens over the same HTTP surface the handlers validate.</p>
 */
public final class IamE2eTestSupport {

    public static final String ADMIN_EMAIL = "admin@eduworkshop.local";
    public static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    private final int port;
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public IamE2eTestSupport(int port, HttpClient client, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.port = port;
        this.client = client;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Re-seeds the bootstrap admin's password to {@link #ADMIN_PASSWORD} and lifts mcp. */
    public void seedAdmin(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        // Upsert (H2 PostgreSQL mode) so the bootstrap admin always exists with a known password,
        // regardless of whether another test class wiped the row in the shared in-memory DB.
        jdbcTemplate.update("""
                MERGE INTO iam_users (id, email, password_hash, status, full_name, must_change_password,
                                      failed_login_attempts, lockout_count, locked_until, last_locked_at,
                                      created_at, updated_at, version)
                KEY (email)
                VALUES (?, ?, ?, 'ACTIVE', 'System Administrator', FALSE, 0, 0, NULL, NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                "00000000-0000-0000-0000-000000000001",
                ADMIN_EMAIL,
                passwordEncoder.encode(ADMIN_PASSWORD));
    }

    /** Self-registered user (USER role), email verified, logged in. */
    public TestUser registerAndLogin() throws Exception {
        return registerAndLoginWithRoles();
    }

    /** Self-registered user granted the given global roles via the admin API, then logged in. */
    public TestUser registerAndLoginWithRoles(String... roles) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";
        UUID userId = register(email, password);
        if (roles.length > 0) {
            grantRoles(loginAsAdmin(), userId, roles);
        }
        String accessToken = login(email, password);
        return new TestUser(userId, email, password, accessToken);
    }

    public String loginAsAdmin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private UUID register(String email, String password) throws Exception {
        HttpResponse<String> response = request("POST", "/api/v1/iam/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Nguyen Van A\"}",
                null);
        if (response.statusCode() != 201) {
            throw new IllegalStateException("register failed: %d %s".formatted(response.statusCode(), response.body()));
        }
        UUID userId = idFromLocation(response);
        seedVerifyToken(userId, "verify-" + email);
        verifyEmail("verify-" + email);
        return userId;
    }

    /** Extracts the caller-generated id from a {@code 201 + Location} response (plan §1.3). */
    public static UUID idFromLocation(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException(
                        "missing Location header: " + response.statusCode() + " " + response.body()));
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    /** Inserts a known raw token + its SHA-256 digest for the given user (test seam, plan §2.2). */
    private void seedVerifyToken(UUID userId, String rawToken) {
        jdbcTemplate.update("""
                INSERT INTO iam_password_reset_tokens (id, user_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(), userId, sha256Hex(rawToken), Instant.now().plusSeconds(3600));
    }

    private void verifyEmail(String token) throws Exception {
        HttpResponse<String> response = request("POST", "/api/v1/iam/auth/verify-email",
                "{\"token\":\"" + token + "\"}", null);
        if (response.statusCode() != 204) {
            throw new IllegalStateException("verify-email failed: %d %s"
                    .formatted(response.statusCode(), response.body()));
        }
    }

    private String login(String email, String password) throws Exception {
        HttpResponse<String> response = request("POST", "/api/v1/iam/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("login failed: %d %s".formatted(response.statusCode(), response.body()));
        }
        return objectMapper.readTree(response.body()).path("accessToken").asText();
    }

    private void grantRoles(String adminToken, UUID userId, String... roles) throws Exception {
        // The admin roles API replaces the FULL role set and requires the base USER role to be
        // present — merge it in (a self-registered account always holds USER).
        LinkedHashSet<String> merged = new LinkedHashSet<>(Arrays.asList(roles));
        merged.add("USER");
        String rolesJson = merged.stream()
                .map(role -> "\"" + role + "\"")
                .collect(Collectors.joining(","));
        HttpResponse<String> response = request("PUT",
                "/api/v1/iam/admin/users/" + userId + "/roles",
                "{\"roles\":[" + rolesJson + "]}", adminToken);
        if (response.statusCode() != 204) {
            throw new IllegalStateException("grant roles failed: %d %s"
                    .formatted(response.statusCode(), response.body()));
        }
    }

    /** SHA-256 hex digest — replicates {@code TokenHash.sha256Hex} for the test-seam token seeding. */
    private static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

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
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** An authenticated user as seen by a business E2E test. */
    public record TestUser(UUID userId, String email, String password, String accessToken) {

        /** The {@code Authorization: Bearer ...} header map to attach to business HTTP calls. */
        public Map<String, String> bearer() {
            return Map.of("Authorization", "Bearer " + accessToken);
        }
    }
}