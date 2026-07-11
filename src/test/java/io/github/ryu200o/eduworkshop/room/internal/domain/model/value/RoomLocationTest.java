package io.github.ryu200o.eduworkshop.room.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomLocationTest {

    @Test
    void of_normalizesBuildingToUppercaseAndTrims() {
        RoomLocation location = RoomLocation.of("  f ", 2);

        assertThat(location.building()).isEqualTo("F");
        assertThat(location.floor()).isEqualTo(2);
        assertThat(location.asString()).isEqualTo("F / Floor 2");
    }

    @Test
    void of_rejectsNonPositiveFloor() {
        assertThatThrownBy(() -> RoomLocation.of("F", 0))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomLocation.of("F", -1))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void of_rejectsBlankBuilding() {
        assertThatThrownBy(() -> RoomLocation.of("   ", 2))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void of_rejectsBuildingContainingDot() {
        assertThatThrownBy(() -> RoomLocation.of("F.B", 2))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void equalityIsBasedOnNormalizedComponents() {
        assertThat(RoomLocation.of("f", 2)).isEqualTo(RoomLocation.of("F", 2));
        assertThat(RoomLocation.of("F", 2)).isNotEqualTo(RoomLocation.of("F", 3));
    }
}
