package io.github.ryu200o.eduworkshop.iam.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound configuration for the IAM security layer ({@code app.iam.*}), mirroring the Workshop
 * {@code WorkshopCheckInProperties} pattern. The JWT secret is a base64-encoded HS256 key
 * (at least 32 bytes decoded) and must be treated as a secret.
 *
 * <p>Flat record on purpose: the {@code jwt} and {@code security} sub-groups are flattened into a
 * single record so relaxed binding maps {@code app.iam.jwt-secret} → {@code jwtSecret} etc. Note:
 * {@code src/test/resources/application.properties} shadows the main file in test contexts, so the
 * {@code app.iam.*} keys are mirrored there too.</p>
 */
@ConfigurationProperties(prefix = "app.iam")
record IamSecurityProperties(
        String jwtSecret,
        int jwtAccessTtlMinutes,
        int jwtRefreshTtlDays,
        int otpTtlHours,
        boolean securityEnabled
) {
}