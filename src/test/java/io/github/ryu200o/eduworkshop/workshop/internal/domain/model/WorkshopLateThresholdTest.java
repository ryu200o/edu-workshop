package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VO purity test for {@link WorkshopLateThreshold} (Epic 3C, OQ-3C-7): range 0..86400 seconds.
 */
class WorkshopLateThresholdTest {

    @Test
    void of_zero_isAllowed() {
        assertThat(WorkshopLateThreshold.of(0).seconds()).isZero();
    }

    @Test
    void of_ceiling_isAllowed() {
        assertThat(WorkshopLateThreshold.of(86400).seconds()).isEqualTo(86400);
    }

    @Test
    void of_typicalValue_isAllowed() {
        assertThat(WorkshopLateThreshold.of(900).seconds()).isEqualTo(900);
    }

    @Test
    void of_negative_rejected() {
        assertThatThrownBy(() -> WorkshopLateThreshold.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_overCeiling_rejected() {
        assertThatThrownBy(() -> WorkshopLateThreshold.of(86401))
                .isInstanceOf(IllegalArgumentException.class);
    }
}