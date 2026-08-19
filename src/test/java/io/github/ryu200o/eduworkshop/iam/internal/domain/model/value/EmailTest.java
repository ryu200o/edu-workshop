package io.github.ryu200o.eduworkshop.iam.internal.domain.model.value;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void of_normalizesToLowercaseAndTrims() {
        assertThat(Email.of("  User@Example.COM ").value()).isEqualTo("user@example.com");
    }

    @Test
    void of_rejectsNull() {
        assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsBlank() {
        assertThatThrownBy(() -> Email.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsMissingAtSign() {
        assertThatThrownBy(() -> Email.of("userexample.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsMultipleAtSigns() {
        assertThatThrownBy(() -> Email.of("user@ex@ample.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsEmptyLocalPart() {
        assertThatThrownBy(() -> Email.of("@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsEmptyDomainPart() {
        assertThatThrownBy(() -> Email.of("user@"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsWhitespaceInside() {
        assertThatThrownBy(() -> Email.of("us er@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityIsBasedOnNormalizedValue() {
        assertThat(Email.of("User@Example.COM")).isEqualTo(Email.of("user@example.com"));
        assertThat(Email.of("a@example.com")).isNotEqualTo(Email.of("b@example.com"));
    }
}
