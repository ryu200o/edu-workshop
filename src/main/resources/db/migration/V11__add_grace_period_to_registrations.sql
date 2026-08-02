-- V11: Add the 12-hour grace cancellation window to registrations.
-- When a workshop is rescheduled, every active (REGISTERED) seat gets an urgent
-- cancellation window of 12 hours from the reschedule moment, bypassing the standard
-- 24h deadline (as long as the workshop has not started). The workshop_start_time
-- snapshot itself is updated by the Application handler (grantGracePeriod), not here.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE registrations
    ADD COLUMN grace_period_until TIMESTAMP WITH TIME ZONE NULL;