package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.security;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts the {@code Bearer} access token, verifies it via {@link AccessTokenCodec}, and populates
 * the {@code SecurityContext} with a {@link AuthenticatedPrincipal} (roles mapped to
 * {@code ROLE_<role>} authorities).
 *
 * <p><strong>mcp gate</strong> (plan §2.2 note #2): a caller with {@code must_change_password=true}
 * is only allowed the whitelisted URIs {@code POST /api/v1/iam/me/change-password} and
 * {@code POST /api/v1/iam/auth/logout}; every other request is short-circuited with HTTP 403 +
 * business code {@code MUST_CHANGE_PASSWORD_FIRST} so clients can steer the user. An invalid/expired
 * token simply clears the context (the request continues unauthenticated and is rejected by the
 * authorization rules / entry point).</p>
 */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MCP_BUSINESS_CODE = "MUST_CHANGE_PASSWORD_FIRST";
    private static final List<String> MCP_WHITELISTED_METHOD_URIS = List.of(
            "POST /api/v1/iam/me/change-password",
            "POST /api/v1/iam/auth/logout"
    );

    private final AccessTokenCodec accessTokenCodec;
    private final ObjectMapper objectMapper;

    JwtAuthenticationFilter(AccessTokenCodec accessTokenCodec, ObjectMapper objectMapper) {
        this.accessTokenCodec = accessTokenCodec;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthenticatedPrincipal principal = accessTokenCodec.decode(token);
                if (principal.mustChangePassword() && !isWhitelisted(request)) {
                    writeMustChangePassword(response);
                    return;
                }
                var authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        return MCP_WHITELISTED_METHOD_URIS.contains(request.getMethod() + " " + request.getRequestURI());
    }

    private void writeMustChangePassword(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You must change your password before continuing."
        );
        problem.setProperty("code", MCP_BUSINESS_CODE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
