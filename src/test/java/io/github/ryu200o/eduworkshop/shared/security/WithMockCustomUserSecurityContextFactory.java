package io.github.ryu200o.eduworkshop.shared.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a {@link SecurityContext} carrying an {@link AuthenticatedPrincipal} for
 * {@link WithMockCustomUser}. Mirrors the production {@code JwtAuthenticationFilter} authority
 * mapping ({@code ROLE_<role>}) so controller-level authorization behaves identically in tests.
 */
public final class WithMockCustomUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.fromString(annotation.userId()),
                annotation.email(),
                Arrays.stream(annotation.roles()).collect(Collectors.toSet()),
                annotation.mustChangePassword());

        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}