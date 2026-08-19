package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA persistence model for a user ({@code iam_users}). Package-private and confined to the outbound
 * persistence adapter — it is an infrastructure detail, entirely separate from the framework-free
 * domain {@code User}. The role rows are an {@code EAGER} child collection keyed by the composite
 * {@link UserRoleId}; the collection is read-only from the JPA perspective (no cascade): role rows
 * are replaced explicitly by the write adapter.
 */
@Entity
@Table(name = "iam_users")
class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "student_code", length = 30)
    private String studentCode;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_locked_at")
    private Instant lastLockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic-locking version (ADR 0015 Strategy B). Persistence concern only — the domain
     * {@code User} never carries it. Null on the create path so Spring Data's {@code isNew()}
     * resolves to {@code true} (persist); set by Hibernate on insert and checked/incremented on
     * each update.
     */
    @Version
    private Long version;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<UserRoleJpaEntity> roles = new ArrayList<>();

    protected UserJpaEntity() {
        // required by JPA
    }

    UserJpaEntity(UUID id, String email, String passwordHash, String status, String fullName,
                  String phoneNumber, String studentCode, String avatarUrl, boolean mustChangePassword,
                  int failedLoginAttempts, int lockoutCount, Instant lockedUntil, Instant lastLockedAt,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.studentCode = studentCode;
        this.avatarUrl = avatarUrl;
        this.mustChangePassword = mustChangePassword;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockoutCount = lockoutCount;
        this.lockedUntil = lockedUntil;
        this.lastLockedAt = lastLockedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    void setEmail(String email) {
        this.email = email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getFullName() {
        return fullName;
    }

    void setFullName(String fullName) {
        this.fullName = fullName;
    }

    String getPhoneNumber() {
        return phoneNumber;
    }

    void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    String getStudentCode() {
        return studentCode;
    }

    void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    String getAvatarUrl() {
        return avatarUrl;
    }

    void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    boolean isMustChangePassword() {
        return mustChangePassword;
    }

    void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    int getLockoutCount() {
        return lockoutCount;
    }

    void setLockoutCount(int lockoutCount) {
        this.lockoutCount = lockoutCount;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    Instant getLastLockedAt() {
        return lastLockedAt;
    }

    void setLastLockedAt(Instant lastLockedAt) {
        this.lastLockedAt = lastLockedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }

    List<UserRoleJpaEntity> getRoles() {
        return roles;
    }

    void setRoles(List<UserRoleJpaEntity> roles) {
        this.roles = roles;
    }
}
