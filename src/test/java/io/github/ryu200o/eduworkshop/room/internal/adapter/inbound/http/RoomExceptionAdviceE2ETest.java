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
}
