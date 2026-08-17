package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading {@link User} aggregates on the write side.
 * Global-uniqueness checks (existsByEmail) live here as Application-level queries, used by handlers
 * for the check-then-execute pattern per ADR 0005 (Revised). Write ports use {@code load*} naming
 * (ADR 0016).
 */
public interface UserRepository {

    /**
     * Persists the mutated User aggregate (write side). saveAndFlush() + DataIntegrityViolation
     * backstop (ADR 0015 Golden Rule 1): the DB unique index on {@code email} is the race-proof gate
     * for concurrent duplicate registrations.
     */
    User save(User user);

    /**
     * Loads the persisted User aggregate by id for write-side mutation. Returns empty when absent.
     */
    Optional<User> loadById(UserId id);

    /**
     * Loads the persisted User aggregate by id, taking a {@code SELECT ... FOR UPDATE} pessimistic
     * write lock (ADR 0015). Serializes concurrent transactions targeting the same aggregate root.
     * Returns empty when absent.
     */
    Optional<User> loadByIdWithLock(UserId id);

    /**
     * Loads the persisted User aggregate by its normalized login email (case-insensitive, LOWER
     * storage). Returns empty when absent.
     */
    Optional<User> loadByEmail(Email email);

    /**
     * {@code true} when a user with the given normalized email already exists. Used by handlers for
     * the fast-fail uniqueness check before calling the aggregate (ADR 0005).
     */
    boolean existsByEmail(Email email);
}
