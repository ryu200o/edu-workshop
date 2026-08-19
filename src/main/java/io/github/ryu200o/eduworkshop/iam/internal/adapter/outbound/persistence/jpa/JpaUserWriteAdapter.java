package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA-backed outbound adapter implementing the User write port ({@link UserRepository}). Handles
 * aggregate mutation, load and the email-uniqueness backstop on the DB unique index. Domain &harr;
 * entity mapping is performed entirely here, keeping the domain framework-free. Persistence exception
 * translation is delegated to {@link JpaUserPersistenceExceptionTranslator}. Package-private; hidden
 * inside the module's {@code internal} boundary.
 */
@Component
class JpaUserWriteAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final UserRoleJpaRepository roleRepository;
    private final JpaUserPersistenceExceptionTranslator exceptionTranslator;

    JpaUserWriteAdapter(UserJpaRepository repository,
                        UserRoleJpaRepository roleRepository,
                        JpaUserPersistenceExceptionTranslator exceptionTranslator) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.exceptionTranslator = exceptionTranslator;
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            // Managed-entity copy pattern (ADR 0015 Strategy B): reuse the persistence-context
            // instance so the @Version column is preserved and checked on flush. saveAndFlush() is
            // kept here (Rule 1) so the DataIntegrityViolationException of the email-unique-index
            // backstop surfaces inside this try-catch for translation.
            UserJpaEntity entity = repository.findById(user.getId().value())
                    .map(existing -> copyTo(existing, user))
                    .orElseGet(() -> toEntity(user));
            repository.saveAndFlush(entity);

            // Role rows are read-only from the JPA perspective (no cascade): replace them explicitly.
            // delete-then-insert is the simplest correct strategy for a small global-role set. The
            // managed entity's child collection is kept in sync so reads within the same persistence
            // context (e.g. save-then-load in one transaction) observe the current role set.
            List<UserRoleJpaEntity> roleEntities = toRoleEntities(user.getId().value(), entity, user.getRoles());
            roleRepository.deleteByUserId(user.getId().value());
            roleRepository.flush();
            roleRepository.saveAll(roleEntities);
            roleRepository.flush();
            entity.setRoles(roleEntities);
        } catch (DataIntegrityViolationException ex) {
            // Race-proof gate (rào lần 2): the DB unique index on email is the authoritative guard
            // against concurrent duplicate registrations. The handler's existsByEmail check is only
            // fail-fast UX (rào lần 1). The violation is translated into domain vocabulary.
            throw exceptionTranslator.translate(ex, user);
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> loadById(UserId id) {
        return repository.findById(id.value()).map(JpaUserWriteAdapter::toUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> loadByIdWithLock(UserId id) {
        return repository.findByIdForUpdate(id.value()).map(JpaUserWriteAdapter::toUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> loadByEmail(Email email) {
        return repository.findByEmail(email.value()).map(JpaUserWriteAdapter::toUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    // ====================== MAPPER ======================

    private static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId().value(),
                user.getEmail().value(),
                user.getPasswordHash(),
                user.getStatus().name(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getStudentCode(),
                user.getAvatarUrl(),
                user.isMustChangePassword(),
                user.getFailedLoginAttempts(),
                user.getLockoutCount(),
                user.getLockedUntil(),
                user.getLastLockedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    /**
     * Copies the mutable business fields of the aggregate onto an existing (managed) entity, leaving
     * {@code id} and {@code version} untouched so Hibernate increments/checks the optimistic-lock
     * version on flush.
     */
    private static UserJpaEntity copyTo(UserJpaEntity entity, User user) {
        entity.setEmail(user.getEmail().value());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setStatus(user.getStatus().name());
        entity.setFullName(user.getFullName());
        entity.setPhoneNumber(user.getPhoneNumber());
        entity.setStudentCode(user.getStudentCode());
        entity.setAvatarUrl(user.getAvatarUrl());
        entity.setMustChangePassword(user.isMustChangePassword());
        entity.setFailedLoginAttempts(user.getFailedLoginAttempts());
        entity.setLockoutCount(user.getLockoutCount());
        entity.setLockedUntil(user.getLockedUntil());
        entity.setLastLockedAt(user.getLastLockedAt());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    private static List<UserRoleJpaEntity> toRoleEntities(UUID userId, UserJpaEntity user,
                                                          Set<GlobalRole> roles) {
        return roles.stream()
                .map(role -> new UserRoleJpaEntity(new UserRoleId(userId, role.name()), user))
                .collect(Collectors.toList());
    }

    private static User toUser(UserJpaEntity entity) {
        Set<GlobalRole> roles = entity.getRoles().stream()
                .map(role -> GlobalRole.valueOf(role.getId().getRole()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return User.reconstruct(
                UserId.of(entity.getId()),
                Email.of(entity.getEmail()),
                entity.getPasswordHash(),
                UserStatus.valueOf(entity.getStatus()),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getStudentCode(),
                entity.getAvatarUrl(),
                entity.isMustChangePassword(),
                entity.getFailedLoginAttempts(),
                entity.getLockoutCount(),
                entity.getLockedUntil(),
                entity.getLastLockedAt(),
                roles,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
