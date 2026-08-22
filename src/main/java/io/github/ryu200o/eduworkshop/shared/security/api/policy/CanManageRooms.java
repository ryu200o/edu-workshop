package io.github.ryu200o.eduworkshop.shared.security.api.policy;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy: caller may manage physical rooms and maintenance schedules (ADR 0023). Granted to
 * {@code FACILITY_MANAGER} or {@code ADMIN}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(" +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).FACILITY_MANAGER, " +
        "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles).ADMIN)")
public @interface CanManageRooms {
}
