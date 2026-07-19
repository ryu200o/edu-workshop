package io.github.ryu200o.eduworkshop.room.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.RoomDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomNameTest {

    @Test
    void of_acceptsFreeFormName_normalizesTrimAndUppercase() {
        assertThat(RoomName.of("F.0201").asString()).isEqualTo("F.0201");
        assertThat(RoomName.of("  lab-101  ").asString()).isEqualTo("LAB-101");
        assertThat(RoomName.of("f.0201").asString()).isEqualTo("F.0201");
    }

    @Test
    void of_keepsBusinessChosenName_intact() {
        assertThat(RoomName.of("LAB-101").asString()).isEqualTo("LAB-101");
        assertThat(RoomName.of("Seminar Room A").asString()).isEqualTo("SEMINAR ROOM A");
    }

    @Test
    void of_rejectsBlank() {
        assertThatThrownBy(() -> RoomName.of(""))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of("   "))
                .isInstanceOf(RoomDomainException.class);
        assertThatThrownBy(() -> RoomName.of(null))
                .isInstanceOf(RoomDomainException.class);
    }

    @Test
    void equalityIsBasedOnDisplayValue() {
        assertThat(RoomName.of("F.0201")).isEqualTo(RoomName.of("F.0201"));
        assertThat(RoomName.of("F.0201")).isNotEqualTo(RoomName.of("F.0215"));
    }
}
