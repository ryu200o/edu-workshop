package io.github.ryu200o.eduworkshop.iam.contract;

import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module display summary of a user's profile (ADR 0020 §1.6). A deliberately lean contract:
 * only the fields business modules render in reports/UI. Users that have no IAM account (historical
 * baseline ids) resolve to the standardized {@link #fallback(UUID)} so consumers never deal with
 * empty optionals — {@code fullName = "Người dùng cũ"}, {@code email = "N/A"},
 * {@code status = "UNKNOWN"}.
 *
 * @param userId    the user id (opaque UUID)
 * @param email     the normalized login email, or {@code "N/A"} for the fallback snapshot
 * @param fullName  the display name, or {@code "Người dùng cũ"} for the fallback snapshot
 * @param avatarUrl optional avatar URL ({@code null} when absent / fallback)
 * @param status    the account status as a string (PENDING_VERIFICATION / ACTIVE / LOCKED /
 *                  DISABLED), or {@code "UNKNOWN"} for the fallback snapshot
 */
public record UserSummarySnapshot(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status
) {

    /**
     * Standardized fallback snapshot for user ids that do not exist in IAM (historical baseline
     * data). Keeps Attendance/Registration reports rendering consistently instead of handling nulls
     * (ADR 0020 §1.6; fullName wording finalized by the architecture ruling, plan §8 Lưu ý 3).
     */
    public static UserSummarySnapshot fallback(UUID userId) {
        return new UserSummarySnapshot(
                Objects.requireNonNull(userId, "userId"),
                "N/A",
                "Người dùng cũ",
                null,
                "UNKNOWN"
        );
    }
}