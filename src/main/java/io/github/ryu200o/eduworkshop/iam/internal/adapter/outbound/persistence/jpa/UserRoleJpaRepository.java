package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link UserRoleJpaEntity}. Package-private. Role rows are replaced
 * (delete-then-insert) by the write adapter on save; the {@code @OneToMany} collection on
 * {@link UserJpaEntity} is read-only (no cascade) so JPA never manages their lifecycle implicitly.
 */
interface UserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, UserRoleId> {

    void deleteByUserId(UUID userId);
}
