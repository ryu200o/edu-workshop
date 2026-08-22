package io.github.ryu200o.eduworkshop.shared.security.api.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class PolicyAnnotationsTest {

    @Test
    void canManageRooms_requiresFacilityManagerOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanManageRooms.class, PreAuthorize.class);
        assertThat(pre).isNotNull();
        assertThat(pre.value()).contains("FACILITY_MANAGER").contains("ADMIN");
    }

    @Test
    void canManageWorkshops_requiresPlannerOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanManageWorkshops.class, PreAuthorize.class);
        assertThat(pre.value()).contains("PLANNER").contains("ADMIN");
    }

    @Test
    void canMarkAttendance_requiresPlannerOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanMarkAttendance.class, PreAuthorize.class);
        assertThat(pre.value()).contains("PLANNER").contains("ADMIN");
    }

    @Test
    void canAuditAttendance_requiresAuditorOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanAuditAttendance.class, PreAuthorize.class);
        assertThat(pre.value()).contains("AUDITOR").contains("ADMIN");
    }

    @Test
    void canVerifyRegistrations_requiresVerifierOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanVerifyRegistrations.class, PreAuthorize.class);
        assertThat(pre.value()).contains("VERIFIER").contains("ADMIN");
    }

    @Test
    void canViewAttendance_requiresPlannerAuditorOrAdmin() {
        PreAuthorize pre = AnnotationUtils.findAnnotation(CanViewAttendance.class, PreAuthorize.class);
        assertThat(pre.value()).contains("PLANNER").contains("AUDITOR").contains("ADMIN");
    }

    @Test
    void allPolicies_referenceSecurityRolesViaTExpression() {
        for (Class<?> policy : new Class<?>[]{CanManageRooms.class, CanManageWorkshops.class,
                CanMarkAttendance.class, CanAuditAttendance.class, CanVerifyRegistrations.class,
                CanViewAttendance.class}) {
            PreAuthorize pre = AnnotationUtils.findAnnotation(policy, PreAuthorize.class);
            assertThat(pre).isNotNull();
            assertThat(pre.value()).contains(
                    "T(io.github.ryu200o.eduworkshop.shared.security.api.SecurityRoles)");
        }
    }
}
