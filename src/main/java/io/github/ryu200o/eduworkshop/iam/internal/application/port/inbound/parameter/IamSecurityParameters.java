package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter;

/**
 * Operational settings for the IAM security layer, published by the bootstrap adapter and injected
 * into the Application handlers and the security adapters — keeps operational policy out of the
 * domain (same pattern as {@code WorkshopCheckInParameters} / {@code WorkshopBufferParameters}).
 *
 * @param jwtSecret          base64-encoded HS256 secret (at least 32 bytes decoded)
 * @param accessTtlMinutes   access-token JWT lifetime in minutes (default 15)
 * @param refreshTtlDays     refresh-token lifetime in days (default 7)
 * @param otpTtlHours        one-time action token (verify-email / reset-password) lifetime in hours
 * @param securityEnabled    master switch for the IAM SecurityFilterChain
 */
public record IamSecurityParameters(
        String jwtSecret,
        int accessTtlMinutes,
        int refreshTtlDays,
        int otpTtlHours,
        boolean securityEnabled
) {
}
