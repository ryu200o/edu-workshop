package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link RefreshTokenJpaEntity}. Package-private — reachable only from
 * within the outbound persistence adapter package.
 */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Loads a token by hash under {@code SELECT ... FOR UPDATE} (ADR 0015) — serializes RTR rotation
     * so two concurrent refreshes of the same token cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Family revoke (OQ-3, RFC 6819): revokes every still-active token of a user in a single bulk
     * UPDATE.
     */
    @Modifying
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :revokedAt "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
