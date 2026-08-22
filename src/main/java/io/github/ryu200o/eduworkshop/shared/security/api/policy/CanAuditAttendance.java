package io.github.ryu200o.eduworkshop.shared.security.api.policy;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy: caller may audit and adjust attendance ledgers (ADR 0023). Granted to {@code AUDITOR} or
 * {@code ADMIN}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(" +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).AUDITOR, " +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).ADMIN)")
public @interface CanAuditAttendance {
}
