# Database Design for IT Workshop Ticket Booking Platform

This document details the database schema, table definitions, indexes, constraints, and design considerations for the IT Workshop Ticket Booking Platform, built on PostgreSQL 17.

## General Principles

*   **UUIDs for Primary Keys**: All primary keys are universally unique identifiers (UUIDs) to facilitate modular databases and prevent reliance on sequential ID coordination.
*   **Timezone-Aware Timestamps**: All timestamp columns use `TIMESTAMP WITH TIME ZONE` (UTC) to ensure correct timezone handling across different server regions and client-server interactions.
*   **Logical References (Zero Cross-Module Foreign Keys)**: To respect Spring Modulith's module boundaries, relationships between tables of different modules are logical references using UUIDs. No physical database-level foreign key constraints are established across modules.
*   **Flyway for Migrations**: Flyway is the single source of truth for the database schema. Hibernate's `ddl-auto` is set to `validate` in local development to ensure the Java entities match the Flyway schema exactly.
*   **JSONB for History Entries**: Business audit history tables (`room_histories`, `workshop_histories`) use PostgreSQL JSONB columns to store flexible, schema-less change payloads. A shared `JsonbConverter` in `shared/` handles serialization via Jackson 3.

---

## Table Definitions

### 1. `rooms` Table (Room Module)

Manages physical locations/venues where workshops are held.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the room. |
| `name` | `VARCHAR` | `NOT NULL`, `UNIQUE` | Room name (e.g., "Auditorium A"). Holds all room names — no distinction between code and name. |
| `capacity` | `INTEGER` | `NOT NULL`, `CHECK (capacity > 0)` | Maximum physical capacity of the room. |
| `location` | `VARCHAR` | `NOT NULL` | Physical location (e.g., "Building B, Floor 3"). |
| `state` | `VARCHAR(20)` | `NOT NULL` | Physical operational state: `ACTIVE`, `MAINTENANCE`, `DEACTIVATED`. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record last update timestamp. |

**Indexes & Constraints**:
*   `uk_rooms_name`: Unique constraint on `name`.
*   `chk_rooms_capacity`: CHECK constraint to ensure capacity is greater than 0.

**Architectural Note on Room States (Static vs. Temporal):**
To ensure high concurrency and prevent database locking, the `rooms` table ONLY stores the *Physical/Static State* of the venue (`ACTIVE`, `UNDER_MAINTENANCE`, `DEACTIVATED`).
* **Temporal States** such as `AVAILABLE` or `OCCUPIED` are time-dependent and **NOT stored in the database**.
* The system dynamic availability is computed at runtime by intersecting a room's physical `ACTIVE` state with the scheduled `workshops` timeline (`start_time`, `end_time`, and `state = 'PUBLISHED'`).

---

### 2. `room_histories` Table (Room Module — Audit)

Business audit log for room mutations. One record per change (create, update, activate, deactivate). Append-only — no updates or deletes.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the history entry. |
| `room_id` | `UUID` | `NOT NULL` | Logical reference to the room (no physical FK). |
| `changed_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | When the change occurred. |
| `changed_by` | `UUID` | `NOT NULL` | Logical reference to the user who made the change. |
| `reason` | `VARCHAR(255)` | `NULLABLE` | Human-readable reason for the change. |
| `changes` | `JSONB` | `NOT NULL` | Full diff of what changed (field-level before/after values). |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |

**Indexes**:
*   `idx_room_histories_room_id`: Index on `room_id` for room-specific history queries.
*   `idx_room_histories_changed_at`: Index on `changed_at` for time-range queries.

---

### 3. `workshops` Table (Workshop Module)

Manages the lifecycle of workshops — from draft through scheduling, publishing, delivery, and completion.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the workshop. |
| `title` | `VARCHAR(200)` | `NOT NULL` | Title of the workshop. |
| `description` | `VARCHAR(2000)` | `NULLABLE` | Detailed overview of the workshop. |
| `room_id` | `UUID` | `NULLABLE` | Logical reference to the room. **Nullable** — a workshop can exist in DRAFT state without a room assigned. |
| `room_display_name_snapshot` | `VARCHAR(200)` | `NULLABLE` | Snapshot of the room's display name at scheduling/publishing time. |
| `start_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Scheduled start time (UTC). |
| `end_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Scheduled end time (UTC). |
| `capacity` | `INTEGER` | `NOT NULL` | Business registration capacity limit (can differ from room capacity). |
| `state` | `VARCHAR(20)` | `NOT NULL` | Workshop lifecycle state: `DRAFT`, `PUBLISHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record last update timestamp. |

**Indexes & Constraints**:
*   `chk_workshops_time`: CHECK constraint to ensure `end_time > start_time`.
*   `idx_workshops_room_id`: Index on `room_id` for room utilization queries.

**Notes**:
*   `room_id` is nullable because workshops can be created in `DRAFT` state without a room assigned. The room is assigned during `schedule()` or `publish()`.
*   `capacity` is the business registration limit. It is independent of the room's physical capacity (the room may hold more people, but the workshop may be limited for pedagogical reasons).
*   The `state` column (not `status`) represents the workshop lifecycle. The column was renamed from `status` to `state` via Flyway V5.

---

### 4. `workshop_histories` Table (Workshop Module — Audit)

Business audit log for workshop lifecycle events. One record per state change, content update, or cross-module event. Append-only — no updates or deletes.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the history entry. |
| `workshop_id` | `UUID` | `NOT NULL` | Logical reference to the workshop (no physical FK). |
| `event_type` | `VARCHAR(50)` | `NOT NULL` | Type of event (e.g., `DRAFT_CREATED`, `PUBLISHED`, `ROOM_RENAMED`). |
| `event_data` | `JSONB` | `NOT NULL` | Event-specific payload capturing what changed. |
| `reason` | `VARCHAR(255)` | `NULLABLE` | Human-readable reason for the event. |
| `changed_by` | `UUID` | `NOT NULL` | Logical reference to the user who triggered the event. |
| `occurred_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | When the event occurred. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |

**Indexes**:
*   `idx_workshop_histories_workshop_id`: Index on `workshop_id` for workshop-specific history queries.
*   `idx_workshop_histories_occurred_at`: Index on `occurred_at` for time-range queries.
*   `idx_workshop_histories_event_type`: Index on `event_type` for filtering by event type.

**Event types recorded**: `DRAFT_CREATED`, `CONTENT_UPDATED`, `SCHEDULED`, `PUBLISHED`, `RESCHEDULED`, `STARTED`, `COMPLETED`, `CANCELLED`, plus cross-module events from `RoomEventHandler` (`ROOM_RENAMED`, `ROOM_RENAMED_DURING_SESSION`, `ROOM_LOCATION_CHANGED`, `ROOM_LOCATION_CHANGED_EMERGENCY`, `ROOM_DEACTIVATED`).

---

### 5. `workshop_snapshots` Table (Workshop Module — Report)

Immutable report created once when a workshop transitions to `COMPLETED`. Captures a denormalized snapshot of the workshop's final state for reporting and analytics. One snapshot per workshop (enforced by unique index).

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the snapshot. |
| `workshop_id` | `UUID` | `NOT NULL`, `UNIQUE` | Logical reference to the workshop. One snapshot per workshop. |
| `room_name` | `VARCHAR(255)` | `NOT NULL` | Denormalized room display name at completion. |
| `room_location` | `VARCHAR(255)` | `NOT NULL` | Denormalized room location at completion. |
| `start_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Workshop start time. |
| `end_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Workshop end time. |
| `capacity` | `INTEGER` | `NOT NULL` | Business registration capacity. |
| `actual_attendance` | `INTEGER` | `NOT NULL` | Number of attendees who checked in. Updated asynchronously by Registration module. |
| `feedback_score` | `NUMERIC(3,2)` | `NULLABLE` | Optional feedback score (0.00–9.99). |
| `completed_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | When the workshop was completed. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |

**Indexes**:
*   `uk_workshop_snapshots_workshop`: Unique constraint on `workshop_id` (one snapshot per workshop).
*   `idx_workshop_snapshots_completed_at`: Index on `completed_at` for reporting queries.

---

### 6. `registrations` Table (Registration Module)

Records user ticket bookings for workshops, including check-in tracking.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the registration. |
| `workshop_id` | `UUID` | `NOT NULL` | Logical reference to the registered workshop (no physical FK). |
| `user_id` | `UUID` | `NOT NULL` | Logical reference to the registering user (no physical FK). |
| `status` | `VARCHAR(50)` | `NOT NULL` | Registration status: `CONFIRMED`, `CANCELLED`, `ATTENDED`, `NO_SHOW`. |
| `registration_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Timestamp when the booking was made. |
| `checked_in` | `BOOLEAN` | `DEFAULT FALSE` | Whether the user has checked in to the workshop. |
| `checked_in_at` | `TIMESTAMP WITH TIME ZONE` | `NULLABLE` | Timestamp when check-in occurred. |
| `checked_in_by` | `UUID` | `NULLABLE` | Logical reference to the user who performed the check-in. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record creation timestamp. |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Record last update timestamp. |

**Indexes & Constraints**:
*   `uk_registrations_workshop_user`: Unique constraint on `(workshop_id, user_id)` to prevent double booking.
*   `idx_registrations_workshop_id`: Index on `workshop_id` for workshop-specific queries.
*   `idx_registrations_user_id`: Index on `user_id` for user registration history.
*   `idx_registrations_status`: Index on `status` for filtering by registration status.
*   `idx_registrations_checked_in`: Index on `checked_in` for check-in queries.

---

### 7. `event_publication` Table (Spring Modulith Outbox Event Registry)

Stores published domain events waiting for asynchronous outbox delivery. Managed by Spring Modulith.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Unique identifier for the event publication. |
| `listener_id` | `TEXT` | `NOT NULL` | Identifier of the consuming event listener (fully qualified class/method name). |
| `event_type` | `TEXT` | `NOT NULL` | Fully qualified class name of the published event type. |
| `serialized_event` | `TEXT` | `NOT NULL` | Serialized JSON payload of the event. |
| `publication_date` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | Timestamp when the event was published. |
| `completion_date` | `TIMESTAMP WITH TIME ZONE` | `NULLABLE` | Timestamp when event processing completed. |
| `status` | `TEXT` | `NULLABLE` | Delivery status (e.g. `PUBLISHED`). |
| `completion_attempts` | `INTEGER` | `NULLABLE` | Count of consumption retry attempts. |
| `last_resubmission_date` | `TIMESTAMP WITH TIME ZONE` | `NULLABLE` | Timestamp of the last retry attempt. |

**Indexes & Constraints**:
*   `event_publication_by_completion_date_idx`: Index on `completion_date` to quickly query uncompleted events for retry scheduling.

---

## Audit History — 3-Step Data Strategy

Each module follows a **3-step data lifecycle** for auditing and traceability:

| Layer | Table(s) | Purpose | Managed By |
|-------|----------|---------|------------|
| **1. Live** | `rooms`, `workshops`, `registrations` | Current operational state. Volatile; can be updated at any time. | Entity (JPA) |
| **2. History** | `room_histories`, `workshop_histories` | Immutable audit log of all changes. Stores JSONB payloads capturing what changed, when, and by whom. | Separate Entity + Repository |
| **3. Snapshot** | `workshop_snapshots` | Frozen record of post-completion state. Created once when a workshop completes. Includes `actual_attendance`, `feedback_score`. | Separate Entity + Repository |

### Key Rules

*   **History entries are append-only** — never updated or deleted.
*   **Snapshots are write-once** — created exactly once per workshop.
*   **JSONB columns** use a JPA `@Convert` with `JsonbConverter` (wraps Jackson 3 `ObjectMapper`).
*   **`event_publication`** outbox table (managed by Spring Modulith) provides at-least-once delivery guarantees for cross-module event processing. This is distinct from the business audit history tables.

---

## Cross-Module Reference Strategy

All inter-module references use **logical UUIDs** — no physical foreign keys exist across module boundaries:

| Source Module | Target Module | Column | FK Constraint |
|---------------|---------------|--------|---------------|
| Workshop | Room | `workshops.room_id` | None (logical UUID) |
| Registration | Workshop | `registrations.workshop_id` | None (logical UUID) |
| Registration | User | `registrations.user_id` | None (logical UUID) |
| Room History | Room | `room_histories.room_id` | None (logical UUID) |
| Workshop History | Workshop | `workshop_histories.workshop_id` | None (logical UUID) |
| Workshop Snapshot | Workshop | `workshop_snapshots.workshop_id` | None (logical UUID) |

This approach respects Spring Modulith's module boundaries. Each module owns and manages its own tables independently.

---

## Shared Infrastructure

### JsonbConverter

A shared `JsonbConverter` (`AttributeConverter<Map<String, Object>, String>`) in the `shared/` package handles JSONB serialization/deserialization. It uses Jackson 3 (`tools.jackson.databind`) and is applied automatically via `@Converter(autoApply = true)`.

Used by:
*   `room_histories.changes` — field-level change diffs
*   `workshop_histories.event_data` — event-specific payloads
