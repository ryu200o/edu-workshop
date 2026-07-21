package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Lifecycle state of a workshop aggregate.
 *
 * <p>This is the Workshop module's own state machine (CREATE → SCHEDULE → PUBLISH, with later
 * IN_PROGRESS / COMPLETED / CANCELLED slices). It is intentionally distinct from Room's physical
 * {@code RoomState}: per ADR 0008, {@code SCHEDULED} is a <em>planning</em> state (no exclusive room
 * reservation; overlapping schedules allowed) while {@code PUBLISHED} is a <em>reservation</em> (the room
 * is owned for the time window, enforced by the Application layer at publish time).</p>
 *
 * <p>Per {@code .AGENTS.md} only {@code *ExposeAPI} is public at module root, so this enum lives inside
 * {@code internal} and is surfaced to other modules only through {@code WorkshopExposeAPI} when needed.</p>
 */
public enum WorkshopState {

    /** Born state: title/description set, no room/time/capacity yet. */
    DRAFT,

    /** Room, time, and capacity are assigned and locally validated. Planning only — room NOT reserved. */
    SCHEDULED,

    /** Room is reserved for the time window (Application-layer conflict check passed at publish time). */
    PUBLISHED,

    /** Workshop is currently running. (Future slice.) */
    IN_PROGRESS,

    /** Workshop finished successfully. (Future slice.) */
    COMPLETED,

    /** Workshop cancelled before/during. (Future slice.) */
    CANCELLED
}
