package io.github.ryu200o.eduworkshop.shared.security.api;

/**
 * Canonical global RBAC role-name constants (ADR 0023). Referenced by the Policy Meta-Annotations via
 * SpEL {@code T(SecurityRoles).X} and by tests, eliminating hard-coded role strings (zero-hardcode).
 */
public final class SecurityRoles {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String PLANNER = "PLANNER";
    public static final String AUDITOR = "AUDITOR";
    public static final String VERIFIER = "VERIFIER";
    public static final String FACILITY_MANAGER = "FACILITY_MANAGER";

    private SecurityRoles() {
    }
}
