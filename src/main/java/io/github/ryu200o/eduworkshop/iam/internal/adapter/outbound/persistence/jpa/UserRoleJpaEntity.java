package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * JPA persistence model for one global role row of a user ({@code iam_user_roles}, composite PK
 * {@code (user_id, role)}). Package-private and confined to the outbound persistence adapter — it is
 * an infrastructure detail, entirely separate from the framework-free domain model.
 */
@Entity
@Table(name = "iam_user_roles")
class UserRoleJpaEntity {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserJpaEntity user;

    protected UserRoleJpaEntity() {
        // required by JPA
    }

    UserRoleJpaEntity(UserRoleId id, UserJpaEntity user) {
        this.id = id;
        this.user = user;
    }

    UserRoleId getId() {
        return id;
    }

    void setId(UserRoleId id) {
        this.id = id;
    }

    UserJpaEntity getUser() {
        return user;
    }

    void setUser(UserJpaEntity user) {
        this.user = user;
    }
}
