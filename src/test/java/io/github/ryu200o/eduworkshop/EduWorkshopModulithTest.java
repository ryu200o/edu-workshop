package io.github.ryu200o.eduworkshop;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the modular monolith boundary (outer ring, ADR 0010): the module dependency graph must stay
 * acyclic. Regresses the Phase 3 flaw where {@code Workshop -> Registration} (via the capacity handler)
 * closed a cycle with the pre-existing {@code Registration -> Workshop} edges. All cross-module reads now
 * flow Workshop → shared {@code QueryBus} ← Registration.
 */
class EduWorkshopModulithTest {

    @Test
    void modulesHaveNoForbiddenOrCyclicDependencies() {
        ApplicationModules modules = ApplicationModules.of(EduWorkshopApplication.class);

        assertThatCode(modules::verify).doesNotThrowAnyException();
    }
}
