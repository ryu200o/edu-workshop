package io.github.ryu200o.eduworkshop.iam;

import io.github.ryu200o.eduworkshop.iam.contract.UserSummarySnapshot;

import java.util.UUID;

/**
 * Public module facade for the IAM module (cross-module API, ADR 0010). Business modules (e.g.
 * Attendance/Registration reporting) look up a user's display summary through this interface only —
 * they never import IAM internals.
 *
 * <p>Lookups for IDs that do not exist in IAM (e.g. historical baseline data) resolve to
 * {@link UserSummarySnapshot#fallback(UUID)} so consumers always render consistently instead of
 * handling nulls (ADR 0020 §1.6).</p>
 */
public interface IamExposeAPI {

    /**
     * Returns the display summary for the given user id, or the standardized fallback snapshot when
     * the id has no IAM account ({@link UserSummarySnapshot#fallback(UUID)}).
     */
    UserSummarySnapshot getUserSummary(UUID userId);
}