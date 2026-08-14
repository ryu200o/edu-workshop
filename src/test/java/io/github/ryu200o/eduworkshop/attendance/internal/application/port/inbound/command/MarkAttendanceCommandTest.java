package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.Actor;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorId;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the <em>input invariants</em> of {@link MarkAttendanceCommand}. Per Domain Discovery
 * Round 2, the batch carries only the learners being acted on, and a {@code studentId} must appear
 * at most once — a duplicate would silently append two entries for the same learner. Enforced in the
 * compact constructor ({@link IllegalArgumentException} → HTTP 400), not in the Aggregate.
 */
class MarkAttendanceCommandTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Actor TRAINER = new Actor(ActorId.of(UUID.randomUUID()), ActorRole.TRAINER);

    @Test
    void acceptsDistinctStudentIds() {
        assertThatCode(() -> new MarkAttendanceCommand(WORKSHOP_ID, List.of(
                new MarkAttendanceCommand.MarkItem(UUID.randomUUID(), AttendanceResult.PRESENT, null),
                new MarkAttendanceCommand.MarkItem(UUID.randomUUID(), AttendanceResult.LATE, "late")), TRAINER))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateStudentId_inSameBatch() {
        UUID studentId = UUID.randomUUID();

        assertThatThrownBy(() -> new MarkAttendanceCommand(WORKSHOP_ID, List.of(
                new MarkAttendanceCommand.MarkItem(studentId, AttendanceResult.PRESENT, null),
                new MarkAttendanceCommand.MarkItem(studentId, AttendanceResult.LATE, "contradicts previous")), TRAINER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate studentId");
    }

    @Test
    void rejectsNullWorkshopId() {
        assertThatThrownBy(() -> new MarkAttendanceCommand(null, List.of(
                new MarkAttendanceCommand.MarkItem(UUID.randomUUID(), AttendanceResult.PRESENT, null)), TRAINER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullItems() {
        assertThatThrownBy(() -> new MarkAttendanceCommand(WORKSHOP_ID, null, TRAINER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullActor() {
        assertThatThrownBy(() -> new MarkAttendanceCommand(WORKSHOP_ID, List.of(
                new MarkAttendanceCommand.MarkItem(UUID.randomUUID(), AttendanceResult.PRESENT, null)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
