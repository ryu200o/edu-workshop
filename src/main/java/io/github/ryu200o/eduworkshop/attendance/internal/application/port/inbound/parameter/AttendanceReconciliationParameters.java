package io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.parameter;

/**
 * Operational Policy for the Attendance Reconciliation Window (ADR 0019 §4, OQ-4) — an Operational
 * Setting supplied by the Application layer, NOT a domain constant.
 *
 * <p>The window is anchored to {@code reconciliationStartedAt} (= {@code WorkshopCompleted.completedAt})
 * and closes at {@code reconciliationStartedAt + windowMinutes}. The Application layer computes the
 * concrete deadline from this policy and passes it into the domain; the aggregate never references
 * this type and never re-derives the window from its own state.</p>
 *
 * @param windowMinutes how long (in minutes) the Reconciliation Window stays open after a workshop
 *                      completes (default 1440 = 24h)
 */
public record AttendanceReconciliationParameters(
        int windowMinutes
) {
}