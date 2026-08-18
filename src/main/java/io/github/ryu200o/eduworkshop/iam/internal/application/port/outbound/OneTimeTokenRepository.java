package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading {@link OneTimeToken} entities — the shared
 * single-use token backing both the verify-email and forgot-password/reset-password flows
 * (OQ-1 RESOLVED). Encapsulates the {@code iam_password_reset_tokens} table; the purpose
 * discriminator lives in the Application handler, never in the persistence model.
 */
public interface OneTimeTokenRepository {

    /**
     * Persists a one-time token (issuance) or its mutated state (consumption). saveAndFlush() per
     * ADR 0015 Golden Rule 1.
     */
    OneTimeToken save(OneTimeToken token);

    /**
     * Loads a one-time token by its hash under a {@code SELECT ... FOR UPDATE} pessimistic write lock
     * (ADR 0015) — the single-use race gate: two concurrent verify/reset attempts on the same token
     * serialize so only one can mark it used.
     */
    Optional<OneTimeToken> loadByHashWithLock(String tokenHash);
}
