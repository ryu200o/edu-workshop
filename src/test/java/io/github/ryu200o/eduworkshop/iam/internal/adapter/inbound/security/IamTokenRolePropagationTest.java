package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;
import tools.jackson.databind.ObjectMapper;

class IamTokenRolePropagationTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private NimbusJwtAccessTokenCodec codec() {
        return new NimbusJwtAccessTokenCodec(new IamSecurityParameters(SECRET, 15, 7, 1, true));
    }

    private JwtAuthenticationFilter filter(NimbusJwtAccessTokenCodec codec) {
        return new JwtAuthenticationFilter(codec, new ObjectMapper());
    }

    @Test
    void facilityManagerRole_propagatesToGrantedAuthority() throws Exception {
        SecurityContextHolder.clearContext();
        Set<String> roles = new LinkedHashSet<>(Set.of("USER", "FACILITY_MANAGER"));
        String token = codec().encode(new AccessTokenCodec.AccessTokenClaims(
                UUID.randomUUID(), "fm@example.com", roles, false,
                Instant.now(), Instant.now().plusSeconds(3600)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        HttpServletResponse response = new MockHttpServletResponse();
        filter(codec()).doFilterInternal(request, response, (req, res) -> {
        });

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        var authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities)
                .contains("ROLE_FACILITY_MANAGER")
                .contains("ROLE_USER");
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedPrincipal.class);
        assertThat(((AuthenticatedPrincipal) auth.getPrincipal()).roles()).contains("FACILITY_MANAGER");
        SecurityContextHolder.clearContext();
    }
}
