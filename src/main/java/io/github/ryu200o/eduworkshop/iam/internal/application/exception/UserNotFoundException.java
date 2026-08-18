package io.github.ryu200o.eduworkshop.iam.internal.application.exception;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Raised by an Application handler when a {@code User} aggregate lookup returns empty in a flow where
 * the user must exist (e.g. verifying/resetting a token whose owner was deleted). Extends
 * {@link ResourceNotFoundException} (AGENTS.md lookup-failure category).
 */
public final class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UserId userId) {
        super("User", "id", userId.value());
    }
}
