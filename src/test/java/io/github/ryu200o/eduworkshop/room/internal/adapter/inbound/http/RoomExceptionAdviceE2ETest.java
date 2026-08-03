package io.github.ryu200o.eduworkshop.room.internal.adapter.inbound.http;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP test proving the Room module's error contract: business exceptions are returned as
 * RFC 9457 {@code ProblemDetail} bodies ({@code application/problem+json}) — the same shape Spring
 * Boot already uses for framework errors and the other modules use — instead of a bare string.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomExceptionAdviceE2ETest.PermitAllSecurity.class)
class RoomExceptionAdviceE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

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

    private HttpResponse<String> createRoom(Map<String, Object> room) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/rooms"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"building\": \"%s\", \"floor\": %d, \"code\": %d, \"name\": \"%s\", \"capacity\": %d}"
                                .formatted(room.get("building"), room.get("floor"), room.get("code"),
                                        room.get("name"), room.get("capacity"))))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void duplicateCodeConflict_isReturnedAsProblemDetail() throws Exception {
        HttpResponse<String> first = createRoom(Map.of(
                "building", "PROBLEM", "floor", 1, "code", 7, "name", "PROBLEM-ROOM-7", "capacity", 50));
        assertThat(first.statusCode()).as("first room: %s", first.body()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> duplicate = createRoom(Map.of(
                "building", "PROBLEM", "floor", 1, "code", 7, "name", "PROBLEM-ROOM-OTHER", "capacity", 50));

        assertThat(duplicate.statusCode()).as("duplicate code: %s", duplicate.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(duplicate.headers().firstValue("Content-Type").orElse(""))
                .as("content-type: %s", duplicate.body())
                .contains("application/problem+json");
        assertThat(duplicate.body()).contains("\"status\":409").contains("\"detail\":");
    }

    @Test
    void overlappingMaintenanceSchedules_return409Conflict() throws Exception {
        HttpResponse<String> created = createRoom(Map.of(
                "building", "PROBLEM", "floor", 2, "code", 8, "name", "PROBLEM-ROOM-8", "capacity", 50));
        assertThat(created.statusCode()).as("room: %s", created.body()).isEqualTo(HttpStatus.OK.value());
        String roomId = extractId(created.body());

        String reason = "Quarterly HVAC filter replacement and duct cleaning";
        HttpResponse<String> first = scheduleMaintenance(roomId, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z", reason);
        assertThat(first.statusCode()).as("first schedule: %s", first.body()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> overlap = scheduleMaintenance(roomId, "2026-09-01T10:00:00Z", "2026-09-01T14:00:00Z", reason);

        assertThat(overlap.statusCode()).as("overlap: %s", overlap.body())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(overlap.headers().firstValue("Content-Type").orElse(""))
                .as("content-type: %s", overlap.body())
                .contains("application/problem+json");
        assertThat(overlap.body()).contains("\"status\":409").contains("\"detail\":");
    }

    private HttpResponse<String> scheduleMaintenance(String roomId, String start, String end, String reason) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/v1/rooms/" + roomId + "/maintenance-schedules"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"startTime\": \"%s\", \"endTime\": \"%s\", \"reason\": \"%s\", \"operator\": \"e2e\"}"
                                .formatted(start, end, reason)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String extractId(String body) {
        return body.replaceAll(".*\\\"id\\\"\\s*:\\s*\\\"([^\"]+)\\\".*", "$1");
    }
}
