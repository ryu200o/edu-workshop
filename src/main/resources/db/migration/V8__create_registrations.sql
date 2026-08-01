-- V8: Create the Registration module table (registrations).
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
--
-- Schema decisions (see ADR 0012 and docs/db/database.md §6):
--  * EXACTLY ONE row per (workshop_id, user_id) pair: cancelling flips status to 'CANCELLED' and
--    re-registering flips it back to 'REGISTERED'. This keeps the uniqueness gate a PLAIN unique
--    index (portable across H2/PostgreSQL — H2 does not support partial/filtered indexes) while
--    still honouring the business rule "at most one active seat per pair, re-registration after a
--    cancellation is allowed".
--  * workshop_id / user_id are LOGICAL references (no physical FK): Registration stays decoupled
--    from Workshop, and there is deliberately NO User module (SA+PO decision) — user_id is the
--    subject id only.
--  * workshop_start_time is a selective snapshot (ADR 0007) so the 24h cancellation-deadline can be
--    enforced inside the Registration BC without temporal coupling to Workshop at cancellation time.

CREATE TABLE registrations (
    id                  UUID PRIMARY KEY,
    workshop_id         UUID NOT NULL,
    user_id             UUID NOT NULL,
    status              VARCHAR(20) NOT NULL,
    workshop_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_registrations_workshop_user ON registrations (workshop_id, user_id);
CREATE INDEX idx_registrations_workshop_id ON registrations (workshop_id);
CREATE INDEX idx_registrations_user_id ON registrations (user_id);
CREATE INDEX idx_registrations_status ON registrations (status);
