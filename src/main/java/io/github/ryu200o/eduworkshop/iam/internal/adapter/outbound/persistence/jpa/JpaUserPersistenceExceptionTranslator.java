package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserPersistenceException;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Converts persistence-provider failures into exceptions meaningful to upper layers. Business
 * conflicts are translated into Application exceptions (email uniqueness → {@link DuplicateEmailException});
 * unexpected persistence failures become {@link UserPersistenceException}. Constraint detection is
 * encapsulated behind business-oriented helpers and never surfaces constraint names as the public API.
 */
@Component
class JpaUserPersistenceExceptionTranslator {

    private static final String CONSTRAINT_EMAIL_UNIQUE = "uk_iam_users_email_lower";

    RuntimeException translate(DataIntegrityViolationException ex, User user) {
        if (representsDuplicateEmail(ex)) {
            return new DuplicateEmailException(user.getEmail());
        }
        return new UserPersistenceException(
                "Cannot save user with email '" + user.getEmail().value() + "'. A conflict was detected. " +
                        "Please check the information and try again.",
                ex
        );
    }

    // ====================== BUSINESS-CONFLICT DETECTION ======================

    private static boolean representsDuplicateEmail(DataIntegrityViolationException ex) {
        if (ex == null) {
            return false;
        }
        Throwable rootCause = ex.getMostSpecificCause();

        // Preferred: the constraint name reported by the ORM (reliable on real DBs via Hibernate).
        if (rootCause instanceof org.hibernate.exception.ConstraintViolationException cve
                && cve.getConstraintName() != null) {
            return CONSTRAINT_EMAIL_UNIQUE.equalsIgnoreCase(cve.getConstraintName());
        }

        // Fallback: some drivers (e.g. H2) omit the constraint name, but the violation message still
        // contains it (e.g. "PUBLIC.UK_IAM_USERS_EMAIL_LOWER ..."). Match it so the gate stays
        // accurate across providers.
        String message = rootCause.getMessage();
        return message != null && message.toUpperCase().contains(CONSTRAINT_EMAIL_UNIQUE.toUpperCase());
    }

    // (student_code uniqueness is a future admin concern; any such violation falls through to
    //  UserPersistenceException until the admin create-user slice adds its own translator rule.)
}
