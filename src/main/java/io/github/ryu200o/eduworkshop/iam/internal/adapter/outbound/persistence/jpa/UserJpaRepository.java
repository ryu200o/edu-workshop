package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link UserJpaEntity}. Package-private — reachable only from within the
 * outbound persistence adapter package.
 */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    /**
     * Email uniqueness fast-fail gate (case-insensitive; storage is forced lowercase by the V21
     * CHECK + the LOWER normalization done by the domain {@code Email} VO).
     */
    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * Loads a user by id under a {@code SELECT ... FOR UPDATE} pessimistic write lock (ADR 0015).
     * Serializes concurrent transactions that mutate the same aggregate root.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
