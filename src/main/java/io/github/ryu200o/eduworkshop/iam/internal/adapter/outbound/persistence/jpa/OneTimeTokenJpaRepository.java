package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link OneTimeTokenJpaEntity}. Package-private — reachable only from
 * within the outbound persistence adapter package.
 */
interface OneTimeTokenJpaRepository extends JpaRepository<OneTimeTokenJpaEntity, UUID> {

    /**
     * Loads a one-time token by hash under {@code SELECT ... FOR UPDATE} (ADR 0015) — the single-use
     * race gate: concurrent verify/reset attempts on the same token serialize so only one can mark
     * it used.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeTokenJpaEntity t where t.tokenHash = :tokenHash")
    Optional<OneTimeTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
