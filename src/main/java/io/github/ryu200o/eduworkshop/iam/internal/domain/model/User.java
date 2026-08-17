package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate Root of the IAM module — the platform's single identity source (ADR 0020 §1.1).
 *
 * <p>Encapsulates credentials (password hash, lockout state) and profile (full name, contact,
 * student code, avatar) of one account, plus its global RBAC roles. A Rich Domain Model: state
 * mutations are only possible through explicit, intention-revealing behaviors, never through public
 * setters.</p>
 *
 * <p>Lifecycle (see {@link UserStatus}): born {@code PENDING_VERIFICATION} via {@link #create}
 * (or directly {@code ACTIVE} + {@code mustChangePassword} via {@link #createByAdmin}, OQ-4);
 * {@link #verifyEmail} activates a pending account. {@code LOCKED} is entered by escalated
 * brute-force lockout ({@link #recordFailedLogin}) or admin {@link #lock}; it is left by
 * {@link #unlock} (admin) or a successful login. {@code DISABLED} (admin {@link #disable}) is only
 * reverted by {@link #enable} — no time-based auto-recovery.</p>
 *
 * <p>Local invariants are enforced here (status transitions, lockout escalation, base
 * {@code USER} role always present); global / set-based rules (email uniqueness, role policy
 * evaluation) are orchestrated by the Application layer (ADR 0005).</p>
 */
public class User {

    /** Consecutive failed logins that trigger a lockout (ADR 0020 §1.5). */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    /** First lockout window (lockout_count = 1) — 15 minutes (ADR 0020 §1.5). */
    public static final Duration FIRST_LOCKOUT_DURATION = Duration.ofMinutes(15);

    /** Escalated lockout window (lockout_count >= 2) — 60 minutes (ADR 0020 §1.5). */
    public static final Duration ESCALATED_LOCKOUT_DURATION = Duration.ofMinutes(60);

    private final UserId id;
    private final Email email;
    private String passwordHash;
    private UserStatus status;
    private String fullName;
    private String phoneNumber;
    private String studentCode;
    private String avatarUrl;
    private boolean mustChangePassword;
    private int failedLoginAttempts;
    private int lockoutCount;
    private Instant lockedUntil;
    private Instant lastLockedAt;
    private Set<GlobalRole> roles;
    private final Instant createdAt;
    private Instant updatedAt;

    private List<UserDomainEvent> recordedEvents = new ArrayList<>();

    private User(UserId id,
                 Email email,
                 String passwordHash,
                 UserStatus status,
                 String fullName,
                 String phoneNumber,
                 String studentCode,
                 String avatarUrl,
                 boolean mustChangePassword,
                 int failedLoginAttempts,
                 int lockoutCount,
                 Instant lockedUntil,
                 Instant lastLockedAt,
                 Set<GlobalRole> roles,
                 Instant createdAt,
                 Instant updatedAt) {
        this.id = requireNonNull(id, "UserId");
        this.email = requireNonNull(email, "Email");
        this.passwordHash = requireNonNull(passwordHash, "passwordHash");
        this.status = requireNonNull(status, "status");
        this.fullName = requireNonNull(fullName, "fullName");
        this.phoneNumber = phoneNumber;
        this.studentCode = studentCode;
        this.avatarUrl = avatarUrl;
        this.mustChangePassword = mustChangePassword;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockoutCount = lockoutCount;
        this.lockedUntil = lockedUntil;
        this.lastLockedAt = lastLockedAt;
        this.roles = requireNonNull(roles, "roles");
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.updatedAt = requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Registers a new account through the public self-registration flow. The account is born
     * {@code PENDING_VERIFICATION} with the mandatory base role {@link GlobalRole#USER}; it becomes
     * {@code ACTIVE} only after {@link #verifyEmail}. Emits a {@link UserRegistered} event.
     */
    public static User create(UserId id, Email email, String passwordHash, String fullName, Instant now) {
        requireNonNull(id, "UserId");
        requireNonNull(email, "Email");
        requireNonNull(passwordHash, "passwordHash");
        requireNonNull(fullName, "fullName");
        requireNonNull(now, "now");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }

        User user = new User(id, email, passwordHash, UserStatus.PENDING_VERIFICATION,
                fullName, null, null, null,
                false, 0, 0, null, null,
                EnumSet.of(GlobalRole.USER),
                now, now);
        user.recordedEvents.add(new UserRegistered(user.id, user.email, now));
        return user;
    }

    /**
     * Creates an account directly as an admin (OQ-4). The account is {@code ACTIVE} immediately with
     * {@code mustChangePassword = true}; a 48h temporary-password TTL is managed by the Application
     * layer via the one-time token store, not by this aggregate. Emits a {@link UserRegistered} event.
     */
    public static User createByAdmin(UserId id, Email email, String passwordHash, String fullName, Instant now) {
        requireNonNull(id, "UserId");
        requireNonNull(email, "Email");
        requireNonNull(passwordHash, "passwordHash");
        requireNonNull(fullName, "fullName");
        requireNonNull(now, "now");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }

        User user = new User(id, email, passwordHash, UserStatus.ACTIVE,
                fullName, null, null, null,
                true, 0, 0, null, null,
                EnumSet.of(GlobalRole.USER),
                now, now);
        user.recordedEvents.add(new UserRegistered(user.id, user.email, now));
        return user;
    }

    /**
     * Reconstructs an existing aggregate from persisted state. Pure data mapping only: it must NOT
     * impose creation rules nor record any event (no spurious re-validation on read — AGENTS.md).
     */
    public static User reconstruct(UserId id,
                                   Email email,
                                   String passwordHash,
                                   UserStatus status,
                                   String fullName,
                                   String phoneNumber,
                                   String studentCode,
                                   String avatarUrl,
                                   boolean mustChangePassword,
                                   int failedLoginAttempts,
                                   int lockoutCount,
                                   Instant lockedUntil,
                                   Instant lastLockedAt,
                                   Set<GlobalRole> roles,
                                   Instant createdAt,
                                   Instant updatedAt) {
        return new User(id, email, passwordHash, status, fullName, phoneNumber, studentCode, avatarUrl,
                mustChangePassword, failedLoginAttempts, lockoutCount, lockedUntil, lastLockedAt,
                roles, createdAt, updatedAt);
    }

    /**
     * Activates a {@code PENDING_VERIFICATION} account after a successful one-time verify-email token
     * consumption. Idempotent when already {@code ACTIVE}. Emits an {@link EmailVerified}.
     *
     * @throws IllegalUserStateException if the account is {@code LOCKED} or {@code DISABLED}
     */
    public void verifyEmail(Instant now) {
        requireNonNull(now, "now");
        if (status == UserStatus.ACTIVE) {
            return;
        }
        if (status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalUserStateException(id, status, UserStatus.ACTIVE,
                    "A " + status + " account cannot verify its email.");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now;
        this.recordedEvents.add(new EmailVerified(id, now));
    }

    /**
     * Guards authentication against brute-force lockout (ADR 0020 §1.5). Throws
     * {@link UserLockedException} while the account is locked; a lock whose window has elapsed is
     * silently cleared so the next attempt may proceed (escalation memory stays in
     * {@code lockoutCount} until a successful login resets it).
     */
    public void assertNotLocked(Instant now) {
        requireNonNull(now, "now");
        if (status != UserStatus.LOCKED) {
            return;
        }
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            throw new UserLockedException(id, lockedUntil);
        }
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /**
     * Records one failed login attempt. Every {@code MAX_LOGIN_ATTEMPTS} consecutive failures
     * escalates a lockout: first offense locks for {@code FIRST_LOCKOUT_DURATION}
     * (lockout_count = 1); re-offenses lock for {@code ESCALATED_LOCKOUT_DURATION}
     * (lockout_count >= 2). Emits {@link UserLocked} on each lockout.
     *
     * @throws IllegalUserStateException if the account is {@code PENDING_VERIFICATION} or
     *                                   {@code DISABLED} (not in a state that may attempt a login)
     */
    public void recordFailedLogin(Instant now) {
        requireNonNull(now, "now");
        if (status == UserStatus.PENDING_VERIFICATION || status == UserStatus.DISABLED) {
            throw new IllegalUserStateException(id, status, UserStatus.ACTIVE,
                    "A " + status + " account cannot attempt to log in.");
        }
        if (status == UserStatus.LOCKED) {
            return;
        }

        this.failedLoginAttempts++;
        if (failedLoginAttempts < MAX_LOGIN_ATTEMPTS) {
            this.updatedAt = now;
            return;
        }

        this.lockoutCount++;
        Duration duration = lockoutCount == 1 ? FIRST_LOCKOUT_DURATION : ESCALATED_LOCKOUT_DURATION;
        this.status = UserStatus.LOCKED;
        this.lockedUntil = now.plus(duration);
        this.lastLockedAt = now;
        this.updatedAt = now;
        this.recordedEvents.add(new UserLocked(id, lockoutCount, lockedUntil, now));
    }

    /**
     * Records a successful login. Clears the failed-attempt streak and any lockout state
     * (also emitted as {@link UserUnlocked} if the account was still marked {@code LOCKED}).
     *
     * @throws IllegalUserStateException if the account is {@code PENDING_VERIFICATION} or
     *                                   {@code DISABLED}
     */
    public void recordSuccessfulLogin(Instant now) {
        requireNonNull(now, "now");
        if (status == UserStatus.PENDING_VERIFICATION || status == UserStatus.DISABLED) {
            throw new IllegalUserStateException(id, status, UserStatus.ACTIVE,
                    "A " + status + " account cannot log in.");
        }
        boolean wasLocked = status == UserStatus.LOCKED;
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockoutCount = 0;
        this.lockedUntil = null;
        this.lastLockedAt = null;
        this.updatedAt = now;
        if (wasLocked) {
            this.recordedEvents.add(new UserUnlocked(id, now));
        }
    }

    /**
     * Changes the password after proving knowledge of the current one (self-service). Only allowed on
     * an {@code ACTIVE} account; clears the {@code must_change_password} gate. Emits
     * {@link PasswordChanged}.
     */
    public void changePassword(String newPasswordHash, Instant now) {
        requireNonNull(newPasswordHash, "newPasswordHash");
        requireNonNull(now, "now");
        if (newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash must not be blank");
        }
        requireState(UserStatus.ACTIVE, "change the password");

        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        this.updatedAt = now;
        this.recordedEvents.add(new PasswordChanged(id, now));
    }

    /**
     * Resets the password by an admin or system flow (no old-password proof required). Allowed in any
     * state; sets the {@code must_change_password} gate so the user must set a new password on next
     * login. Emits {@link PasswordChanged}.
     */
    public void resetPassword(String newPasswordHash, Instant now) {
        requireNonNull(newPasswordHash, "newPasswordHash");
        requireNonNull(now, "now");
        if (newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash must not be blank");
        }

        this.passwordHash = newPasswordHash;
        this.mustChangePassword = true;
        this.updatedAt = now;
        this.recordedEvents.add(new PasswordChanged(id, now));
    }

    /**
     * Updates the editable profile fields (OQ-5: {@code full_name} required, plus optional
     * {@code phone_number} / {@code student_code} / {@code avatar_url}; email and password are
     * explicitly NOT editable here). Only allowed on an {@code ACTIVE} account. Silent (no event).
     */
    public void updateProfile(String fullName, String phoneNumber, String studentCode, String avatarUrl, Instant now) {
        requireNonNull(fullName, "fullName");
        requireNonNull(now, "now");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        requireState(UserStatus.ACTIVE, "update the profile");

        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.studentCode = studentCode;
        this.avatarUrl = avatarUrl;
        this.updatedAt = now;
    }

    /**
     * Replaces the global role set (admin role management). The mandatory base role
     * {@link GlobalRole#USER} is always enforced and cannot be removed. Emits {@link RolesUpdated}.
     *
     * @throws IllegalArgumentException if {@code newRoles} is null or omits the base {@code USER} role
     */
    public void updateRoles(Set<GlobalRole> newRoles, Instant now) {
        requireNonNull(newRoles, "newRoles");
        requireNonNull(now, "now");
        if (!newRoles.contains(GlobalRole.USER)) {
            throw new IllegalArgumentException("The base role USER must always be present");
        }

        Set<GlobalRole> previous = roles;
        this.roles = EnumSet.copyOf(newRoles);
        this.updatedAt = now;
        this.recordedEvents.add(new RolesUpdated(id, previous, roles, now));
    }

    /**
     * Locks an account explicitly (admin action). Idempotent when already {@code LOCKED} or
     * {@code DISABLED}. Sets an infinite lock window ({@code lockedUntil = null}, no time-based
     * auto-recovery). Emits {@link UserLocked}.
     */
    public void lock(Instant now) {
        requireNonNull(now, "now");
        if (status == UserStatus.LOCKED || status == UserStatus.DISABLED) {
            return;
        }
        this.status = UserStatus.LOCKED;
        this.lockedUntil = null;
        this.lastLockedAt = now;
        this.updatedAt = now;
        this.recordedEvents.add(new UserLocked(id, lockoutCount, null, now));
    }

    /**
     * Unlocks an account explicitly (admin action). Idempotent when not {@code LOCKED}. Emits
     * {@link UserUnlocked}.
     */
    public void unlock(Instant now) {
        requireNonNull(now, "now");
        if (status != UserStatus.LOCKED) {
            return;
        }
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
        this.lastLockedAt = null;
        this.updatedAt = now;
        this.recordedEvents.add(new UserUnlocked(id, now));
    }

    /**
     * Disables an account (admin action). Idempotent when already {@code DISABLED}. Emits
     * {@link UserDisabled}.
     */
    public void disable(Instant now) {
        requireNonNull(now, "now");
        if (status == UserStatus.DISABLED) {
            return;
        }
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
        this.recordedEvents.add(new UserDisabled(id, now));
    }

    /**
     * Re-enables a disabled account back to {@code ACTIVE} (admin action). Idempotent when not
     * {@code DISABLED}. Emits {@link UserEnabled}.
     */
    public void enable(Instant now) {
        requireNonNull(now, "now");
        if (status != UserStatus.DISABLED) {
            return;
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now;
        this.recordedEvents.add(new UserEnabled(id, now));
    }

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public int getLockoutCount() {
        return lockoutCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLockedAt() {
        return lastLockedAt;
    }

    public Set<GlobalRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<UserDomainEvent> recordedEvents() {
        return Collections.unmodifiableList(recordedEvents);
    }

    public void clearRecordedEvents() {
        recordedEvents = new ArrayList<>();
    }

    private void requireState(UserStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalUserStateException(id, status, expected,
                    "Cannot " + operation + ": account is " + status + ", expected " + expected + ".");
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }
}
