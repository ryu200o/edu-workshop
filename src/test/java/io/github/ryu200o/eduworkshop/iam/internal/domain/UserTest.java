package io.github.ryu200o.eduworkshop.iam.internal.domain;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.EmailVerified;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.PasswordChanged;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.RolesUpdated;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserDisabled;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserDomainEvent;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserEnabled;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserLocked;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserRegistered;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserUnlocked;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception.IllegalUserStateException;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception.UserLockedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");
    private static final String PASSWORD_HASH = "$2a$12$abcdefghijklmnopqrstuv";

    private static UserId newId() {
        return UserId.generate();
    }

    private static Email email() {
        return Email.of("student@example.com");
    }

    private static User createActive() {
        User user = User.create(newId(), email(), PASSWORD_HASH, "Nguyen Van A", NOW);
        user.verifyEmail(NOW.plusSeconds(1));
        return user;
    }

    @Test
    void create_registersPendingVerificationWithBaseUserRoleAndEvent() {
        User user = User.create(newId(), email(), PASSWORD_HASH, "Nguyen Van A", NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getRoles()).containsExactly(GlobalRole.USER);
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.recordedEvents()).hasSize(1);
        UserRegistered event = (UserRegistered) user.recordedEvents().get(0);
        assertThat(event.userId()).isEqualTo(user.getId());
        assertThat(event.email()).isEqualTo(user.getEmail());
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void createByAdmin_activatesImmediatelyWithMustChangePassword() {
        User user = User.createByAdmin(newId(), email(), PASSWORD_HASH, "Quan Ly", NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.recordedEvents()).hasSize(1);
        assertThat(user.recordedEvents().get(0)).isInstanceOf(UserRegistered.class);
    }

    @Test
    void create_rejectsBlankFullName() {
        assertThatThrownBy(() -> User.create(newId(), email(), PASSWORD_HASH, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsBlankPasswordHash() {
        assertThatThrownBy(() -> User.create(newId(), email(), "  ", "Nguyen Van A", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyEmail_activatesPendingAccountAndEmitsEvent() {
        User user = User.create(newId(), email(), PASSWORD_HASH, "Nguyen Van A", NOW);
        Instant verifiedAt = NOW.plusSeconds(10);

        user.verifyEmail(verifiedAt);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.recordedEvents()).hasSize(2);
        assertThat(user.recordedEvents().get(1)).isInstanceOf(EmailVerified.class);
    }

    @Test
    void verifyEmail_isIdempotentWhenAlreadyActive() {
        User user = createActive();
        user.clearRecordedEvents();

        user.verifyEmail(NOW.plusSeconds(5));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void verifyEmail_rejectsLockedOrDisabledAccount() {
        User locked = createActive();
        locked.recordFailedLogin(NOW);
        locked.recordFailedLogin(NOW);
        locked.recordFailedLogin(NOW);
        locked.recordFailedLogin(NOW);
        locked.recordFailedLogin(NOW);
        assertThatThrownBy(() -> locked.verifyEmail(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalUserStateException.class);

        User disabled = createActive();
        disabled.disable(NOW);
        assertThatThrownBy(() -> disabled.verifyEmail(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalUserStateException.class);
    }

    @Test
    void assertNotLocked_allowsActiveAccount() {
        User user = createActive();
        user.assertNotLocked(NOW);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void assertNotLocked_throwsWhileWithinLockWindow() {
        User user = createActive();
        lockOut(user, NOW);

        assertThatThrownBy(() -> user.assertNotLocked(NOW.plusSeconds(5)))
                .isInstanceOf(UserLockedException.class);
    }

    @Test
    void assertNotLocked_silentlyAutoUnlocksWhenWindowElapsed() {
        User user = createActive();
        lockOut(user, NOW);
        user.clearRecordedEvents();

        user.assertNotLocked(NOW.plusSeconds(User.FIRST_LOCKOUT_DURATION.toSeconds() + 1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void recordFailedLogin_locksAfterMaxAttemptsWithFirstDuration() {
        User user = createActive();

        for (int i = 0; i < User.MAX_LOGIN_ATTEMPTS - 1; i++) {
            user.recordFailedLogin(NOW);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        user.recordFailedLogin(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockoutCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(User.FIRST_LOCKOUT_DURATION));
        UserLocked event = (UserLocked) user.recordedEvents().get(user.recordedEvents().size() - 1);
        assertThat(event.lockoutCount()).isEqualTo(1);
        assertThat(event.lockedUntil()).isEqualTo(user.getLockedUntil());
    }

    @Test
    void recordFailedLogin_escalatesToEscalatedDurationOnRepeatOffense() {
        User user = createActive();
        lockOut(user, NOW);
        user.assertNotLocked(NOW.plusSeconds(User.FIRST_LOCKOUT_DURATION.toSeconds() + 1));

        for (int i = 0; i < User.MAX_LOGIN_ATTEMPTS - 1; i++) {
            user.recordFailedLogin(NOW);
        }
        user.recordFailedLogin(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockoutCount()).isEqualTo(2);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(User.ESCALATED_LOCKOUT_DURATION));
    }

    @Test
    void recordFailedLogin_rejectsPendingOrDisabledAccount() {
        User pending = User.create(newId(), email(), PASSWORD_HASH, "Nguyen Van A", NOW);
        assertThatThrownBy(() -> pending.recordFailedLogin(NOW))
                .isInstanceOf(IllegalUserStateException.class);

        User disabled = createActive();
        disabled.disable(NOW);
        assertThatThrownBy(() -> disabled.recordFailedLogin(NOW))
                .isInstanceOf(IllegalUserStateException.class);
    }

    @Test
    void recordSuccessfulLogin_clearsStreakLockoutAndEmitsUnlocked() {
        User user = createActive();
        lockOut(user, NOW);

        user.recordSuccessfulLogin(NOW.plusSeconds(User.FIRST_LOCKOUT_DURATION.toSeconds() + 1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockoutCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.recordedEvents().get(user.recordedEvents().size() - 1))
                .isInstanceOf(UserUnlocked.class);
    }

    @Test
    void recordSuccessfulLogin_clearsFailedStreakWithoutUnlockEvent() {
        User user = createActive();
        user.recordFailedLogin(NOW);
        user.recordFailedLogin(NOW);
        user.clearRecordedEvents();

        user.recordSuccessfulLogin(NOW.plusSeconds(1));

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void changePassword_updatesHashAndClearsMustChangePassword() {
        User user = createActive();
        user.resetPassword(PASSWORD_HASH, NOW);
        user.clearRecordedEvents();

        user.changePassword("$2a$12$newhash", NOW.plusSeconds(1));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.recordedEvents()).hasSize(1);
        assertThat(user.recordedEvents().get(0)).isInstanceOf(PasswordChanged.class);
    }

    @Test
    void changePassword_rejectsNonActiveAccount() {
        User pending = User.create(newId(), email(), PASSWORD_HASH, "Nguyen Van A", NOW);
        assertThatThrownBy(() -> pending.changePassword("$2a$12$newhash", NOW))
                .isInstanceOf(IllegalUserStateException.class);
    }

    @Test
    void resetPassword_setsMustChangePasswordInAnyState() {
        User disabled = createActive();
        disabled.disable(NOW);

        disabled.resetPassword("$2a$12$newhash", NOW.plusSeconds(1));

        assertThat(disabled.getPasswordHash()).isEqualTo("$2a$12$newhash");
        assertThat(disabled.isMustChangePassword()).isTrue();
        assertThat(disabled.recordedEvents().get(disabled.recordedEvents().size() - 1))
                .isInstanceOf(PasswordChanged.class);
    }

    @Test
    void updateProfile_updatesEditableFieldsAndIsSilent() {
        User user = createActive();
        user.clearRecordedEvents();

        user.updateProfile("Tran Thi B", "0901234567", "20001234", "https://cdn.example.com/avatar.png", NOW);

        assertThat(user.getFullName()).isEqualTo("Tran Thi B");
        assertThat(user.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(user.getStudentCode()).isEqualTo("20001234");
        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void updateProfile_rejectsBlankFullName() {
        User user = createActive();
        assertThatThrownBy(() -> user.updateProfile("  ", "0901234567", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProfile_rejectsNonActiveAccount() {
        User disabled = createActive();
        disabled.disable(NOW);
        assertThatThrownBy(() -> disabled.updateProfile("Tran Thi B", null, null, null, NOW))
                .isInstanceOf(IllegalUserStateException.class);
    }

    @Test
    void updateRoles_enforcesBaseUserRoleAndEmitsEvent() {
        User user = createActive();
        user.clearRecordedEvents();

        user.updateRoles(EnumSet.of(GlobalRole.USER, GlobalRole.AUDITOR), NOW);

        assertThat(user.getRoles()).containsExactlyInAnyOrder(GlobalRole.USER, GlobalRole.AUDITOR);
        RolesUpdated event = (RolesUpdated) user.recordedEvents().get(0);
        assertThat(event.previousRoles()).containsExactly(GlobalRole.USER);
        assertThat(event.newRoles()).containsExactlyInAnyOrder(GlobalRole.USER, GlobalRole.AUDITOR);
    }

    @Test
    void updateRoles_rejectsMissingBaseUserRole() {
        User user = createActive();
        assertThatThrownBy(() -> user.updateRoles(EnumSet.of(GlobalRole.AUDITOR), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lock_locksWithInfiniteWindowAndEmitsEvent() {
        User user = createActive();
        user.clearRecordedEvents();

        user.lock(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.recordedEvents()).hasSize(1);
        assertThat(user.recordedEvents().get(0)).isInstanceOf(UserLocked.class);
    }

    @Test
    void lock_isIdempotentWhenAlreadyLocked() {
        User user = createActive();
        user.lock(NOW);
        user.clearRecordedEvents();

        user.lock(NOW.plusSeconds(1));

        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void unlock_restoresActiveAndEmitsEvent() {
        User user = createActive();
        user.lock(NOW);
        user.clearRecordedEvents();

        user.unlock(NOW.plusSeconds(1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.recordedEvents()).hasSize(1);
        assertThat(user.recordedEvents().get(0)).isInstanceOf(UserUnlocked.class);
    }

    @Test
    void disable_emitsEventAndEnableReverts() {
        User user = createActive();
        user.clearRecordedEvents();
        user.disable(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.recordedEvents()).hasSize(1);
        assertThat(user.recordedEvents().get(0)).isInstanceOf(UserDisabled.class);

        user.enable(NOW.plusSeconds(1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.recordedEvents()).hasSize(2);
        assertThat(user.recordedEvents().get(1)).isInstanceOf(UserEnabled.class);
    }

    @Test
    void disable_isIdempotent() {
        User user = createActive();
        user.disable(NOW);
        user.clearRecordedEvents();

        user.disable(NOW.plusSeconds(1));

        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void enable_isIdempotentWhenNotDisabled() {
        User user = createActive();
        user.clearRecordedEvents();

        user.enable(NOW);

        assertThat(user.recordedEvents()).isEmpty();
    }

    @Test
    void reconstruct_restoresStateWithoutRecordingEvents() {
        User original = createActive();
        original.updateRoles(EnumSet.of(GlobalRole.USER, GlobalRole.PLANNER), NOW);
        original.updateProfile("Tran Thi B", "0901234567", "20001234", null, NOW);
        original.recordFailedLogin(NOW);
        original.clearRecordedEvents();

        User restored = User.reconstruct(original.getId(), original.getEmail(), original.getPasswordHash(),
                original.getStatus(), original.getFullName(), original.getPhoneNumber(),
                original.getStudentCode(), original.getAvatarUrl(), original.isMustChangePassword(),
                original.getFailedLoginAttempts(), original.getLockoutCount(), original.getLockedUntil(),
                original.getLastLockedAt(), original.getRoles(), original.getCreatedAt(), original.getUpdatedAt());

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getRoles()).isEqualTo(original.getRoles());
        assertThat(restored.getFailedLoginAttempts()).isEqualTo(original.getFailedLoginAttempts());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.recordedEvents()).isEmpty();
    }

    private static void lockOut(User user, Instant at) {
        for (int i = 0; i < User.MAX_LOGIN_ATTEMPTS; i++) {
            user.recordFailedLogin(at);
        }
    }
}
