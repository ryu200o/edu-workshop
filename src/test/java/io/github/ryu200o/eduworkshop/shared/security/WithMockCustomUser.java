package io.github.ryu200o.eduworkshop.shared.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-support annotation (shared-test, plan §7 Slice 5 / §9) that injects a fully-formed
 * {@link AuthenticatedPrincipal} — the same shape the IAM {@code JwtAuthenticationFilter} puts into
 * the {@code SecurityContext} in production — directly into the context for in-process
 * WebMvcTest/IntegrationTest business-module tests, so they do not need an HTTP
 * register→login→Bearer round-trip.
 *
 * <p>Role semantics mirror the production mapping: {@code roles} are the global RBAC roles
 * ({@code USER/ADMIN/PLANNER/AUDITOR/VERIFIER}), mapped to {@code ROLE_<role>} authorities exactly
 * like {@code JwtAuthenticationFilter}. Consuming modules read it via
 * {@code @AuthenticationPrincipal AuthenticatedPrincipal}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory.class)
public @interface WithMockCustomUser {

    String userId();

    String email() default "user@example.com";

    String[] roles() default {"USER"};

    boolean mustChangePassword() default false;
}