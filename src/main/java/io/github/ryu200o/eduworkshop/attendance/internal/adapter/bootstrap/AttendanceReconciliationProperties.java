package io.github.ryu200o.eduworkshop.attendance.internal.adapter.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound properties for the Attendance Reconciliation Window (ADR 0019 §4, OQ-4 — Operational
 * Setting, not a Domain constant).
 *
 * <p>Only key: {@code app.attendance.reconciliation.window-minutes}. The window opens when a
 * workshop completes ({@code reconciliationStartedAt} = {@code WorkshopCompleted.completedAt}) and
 * closes {@code windowMinutes} later; the Application layer derives the concrete deadline.</p>
 */
@ConfigurationProperties(prefix = "app.attendance.reconciliation")
record AttendanceReconciliationProperties(
        int windowMinutes
) {
}