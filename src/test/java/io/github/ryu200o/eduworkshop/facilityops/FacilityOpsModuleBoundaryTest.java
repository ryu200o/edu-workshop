package io.github.ryu200o.eduworkshop.facilityops;

import io.github.ryu200o.eduworkshop.EduWorkshopApplication;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the FacilityOps module boundary: the module sits at the top of the dependency DAG and may
 * only depend on the public {@code *ExposeAPI} interfaces (plus the shared kernel) — never on another
 * module's {@code internal} packages. Delegates to Spring Modulith's {@code ApplicationModules.verify()}
 * (the project's established boundary mechanism — no ArchUnit dependency), which catches forbidden
 * cross-module {@code internal} access, {@code allowedDependencies} violations and cyclic dependencies.
 */
class FacilityOpsModuleBoundaryTest {

    @Test
    void facilityOpsDependsOnlyOnPublicApis() {
        ApplicationModules modules = ApplicationModules.of(EduWorkshopApplication.class);

        assertThatCode(modules::verify).doesNotThrowAnyException();
    }
}
