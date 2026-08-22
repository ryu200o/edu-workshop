package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.security;

import io.github.ryu200o.eduworkshop.shared.security.api.ProblemDetailAccessDeniedHandler;
import io.github.ryu200o.eduworkshop.shared.security.api.ProblemDetailAuthenticationEntryPoint;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * IAM security chain (plan §5). The primary (default) chain for every request: the public auth APIs
 * ({@code POST /api/v1/iam/auth/**}) are {@code permitAll}, the admin surface requires {@code ADMIN},
 * the self-service surface requires authentication, and — since IAM Slice 5 — every business route
 * falls through to {@code anyRequest().authenticated()} (the {@code X-User-Id}/{@code X-Actor-Role}
 * headers and test permit-all chains are gone).
 *
 * <p>Method-level RBAC is enabled globally via {@link EnableMethodSecurity} (ADR 0023) and enforced at
 * the Inbound Controllers through the Policy Meta-Annotations ({@code @CanManageRooms}, ...). The
 * chain (and its JWT filter) is gated by {@code app.iam.security.enabled} (default true) so a
 * deployment can fall back to fully-open security during migration. The {@code PasswordEncoder} stays
 * unconditional — the auth handlers always need it.</p>
 *
 * <p>Authorization failures are rendered uniformly as RFC 7807 {@link ProblemDetail}: {@code 403}
 * (authenticated, missing role) via {@link ProblemDetailAccessDeniedHandler} and {@code 401}
 * (unauthenticated) via {@link ProblemDetailAuthenticationEntryPoint}.</p>
 */
@Configuration
@EnableMethodSecurity
class IamSecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.iam.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain iamSecurityFilterChain(HttpSecurity http,
                                                JwtAuthenticationFilter jwtAuthenticationFilter,
                                                ProblemDetailAccessDeniedHandler accessDeniedHandler,
                                                ProblemDetailAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/iam/auth/**").permitAll()
                        .requestMatchers("/api/v1/iam/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/iam/me/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
