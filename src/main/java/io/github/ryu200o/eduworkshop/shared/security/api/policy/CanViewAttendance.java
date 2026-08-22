package io.github.ryu200o.eduworkshop.shared.security.api.policy;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy: caller may view event attendance data (ADR 0023). Granted to {@code PLANNER},
 * {@code AUDITOR}, or {@code ADMIN}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(" +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).PLANNER, " +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).AUDITOR, " +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).ADMIN)")
public @interface CanViewAttendance {
}
