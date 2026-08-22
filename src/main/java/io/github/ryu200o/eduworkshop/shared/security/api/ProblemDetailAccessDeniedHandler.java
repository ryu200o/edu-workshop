package io.github.ryu200o.eduworkshop.shared.security.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Renders an RFC 7807 {@link ProblemDetail} 403 when method security raises
 * {@link AccessDeniedException} for an authenticated caller lacking the required role (ADR 0023).
 * Replaces the previous ad-hoc role-violation exceptions with the platform-standard error shape.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Access denied: insufficient role for this operation");
        problem.setProperty("code", "ACCESS_DENIED");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
