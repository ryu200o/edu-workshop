package io.github.ryu200o.eduworkshop.room.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomNameTest {

    @Test
    void of_locationAndCode_composesCanonicalName() {
        RoomLocation location = RoomLocation.of("F", 2);

        RoomName name = RoomName.of(location, "01");

        assertThat(name.asString()).isEqualTo("F.201");
        assertThat(name.building()).isEqualTo("F");
        assertThat(name.floor()).isEqualTo(2);
        assertThat(name.code()).isEqualTo("01");
    }

    @Test
    void of_locationAndCode_uppercasesBuilding() {
        RoomLocation location = RoomLocation.of("f", 2);

        assertThat(RoomName.of(location, "15").asString()).isEqualTo("F.215");
    }

    @Test
    void of_raw_parsesValidName() {
        RoomName name = RoomName.of("f.201");

        assertThat(name.building()).isEqualTo("F");
        assertThat(name.floor()).isEqualTo(2);
        assertThat(name.code()).isEqualTo("01");
        assertThat(name.asString()).isEqualTo("F.201");
    }

    @Test
    void of_raw_rejectsWrongFormat() {
        assertThatThrownBy(() -> RoomName.of("F201"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of("F.2"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of("F.2A1"))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void of_code_rejectsNonTwoDigit() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThatThrownBy(() -> RoomName.of(location, "1"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of(location, "001"))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void matches_isTrueWhenBuildingAndFloorAlign() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(RoomName.of("F.201").matches(location)).isTrue();
        assertThat(RoomName.of("F.215").matches(location)).isTrue();
        assertThat(RoomName.of("G.201").matches(location)).isFalse();
        assertThat(RoomName.of("F.301").matches(location)).isFalse();
    }

    @Test
    void equalityIsBasedOnAllComponents() {
        assertThat(RoomName.of("F.201")).isEqualTo(RoomName.of("F.201"));
        assertThat(RoomName.of("F.201")).isNotEqualTo(RoomName.of("F.215"));
    }
}
