package io.github.ryu200o.eduworkshop.room.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomNameTest {

    @Test
    void of_locationAndCode_composesCanonicalName() {
        RoomLocation location = RoomLocation.of("F", 2);

        RoomName name = RoomName.of(location, "01");

        assertThat(name.asString()).isEqualTo("F.0201");
        assertThat(name.building()).isEqualTo("F");
        assertThat(name.floor()).isEqualTo(2);
        assertThat(name.code()).isEqualTo("01");
    }

    @Test
    void of_locationAndCode_zeroPadsFloor() {
        RoomLocation location = RoomLocation.of("F", 5);

        assertThat(RoomName.of(location, "LAB").asString()).isEqualTo("F.05LAB");
        assertThat(RoomName.of(RoomLocation.of("F", 12), "05").asString()).isEqualTo("F.1205");
        assertThat(RoomName.of(RoomLocation.of("F", 105), "205").asString()).isEqualTo("F.105205");
    }

    @Test
    void of_locationAndCode_uppercasesBuildingAndCode() {
        RoomLocation location = RoomLocation.of("f", 2);

        assertThat(RoomName.of(location, "15").asString()).isEqualTo("F.0215");
        assertThat(RoomName.of(location, "01a").asString()).isEqualTo("F.0201A");
    }

    @Test
    void ofRaw_acceptsCanonicalName_withoutThrowing() {
        RoomName name = RoomName.ofRaw("f.0201");

        assertThat(name.asString()).isEqualTo("F.0201");
        assertThat(RoomName.ofRaw("F.0201")).isEqualTo(RoomName.ofRaw("F.0201"));
    }

    @Test
    void ofRaw_rejectsWrongFormat() {
        assertThatThrownBy(() -> RoomName.ofRaw("F201"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.ofRaw("F.2"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.ofRaw("F.2A1"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.ofRaw("F.0ABCDEFGHIJK"))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void of_code_rejectsInvalid() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(RoomName.of(location, "1")).isEqualTo(RoomName.of(location, "1"));
        assertThat(RoomName.of(location, "001")).isEqualTo(RoomName.of(location, "001"));
        assertThat(RoomName.of(location, "LAB")).isEqualTo(RoomName.of(location, "LAB"));
        assertThat(RoomName.of(location, "01A")).isEqualTo(RoomName.of(location, "01A"));

        assertThatThrownBy(() -> RoomName.of(location, ""))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of(location, "A-B"))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of(location, "ABCDEFGHIJK"))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void matches_isTrueWhenBuildingAndFloorAlign() {
        RoomLocation location = RoomLocation.of("F", 2);

        assertThat(RoomName.of(location, "01").matches(location)).isTrue();
        assertThat(RoomName.of(location, "15").matches(location)).isTrue();
        assertThat(RoomName.of(RoomLocation.of("G", 2), "01").matches(location)).isFalse();
        assertThat(RoomName.of(RoomLocation.of("F", 3), "01").matches(location)).isFalse();
    }

    @Test
    void equalityIsBasedOnDisplayValue() {
        assertThat(RoomName.ofRaw("F.0201")).isEqualTo(RoomName.ofRaw("F.0201"));
        assertThat(RoomName.ofRaw("F.0201")).isNotEqualTo(RoomName.ofRaw("F.0215"));
    }
}
