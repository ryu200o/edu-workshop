-- Spring Modulith Event Publication Registry (transactional outbox) table.
-- Copied from the official Modulith schema (spring-modulith-events-jdbc
-- `schemas/v2/schema-postgresql.sql`), with two minimal portability tweaks so the
-- same DDL runs on both H2 (tests, PostgreSQL mode) and PostgreSQL (runtime):
--   1. `TIMESTAMP(9) WITH TIME ZONE` -> `TIMESTAMP WITH TIME ZONE` (Postgres caps
--      precision at 6; H2 accepts the no-precision form).
--   2. Postgres-only `USING hash(serialized_event)` index -> portable composite
--      index `(listener_id, serialized_event)` (the H2 official form), which serves
--      the registry's BY_EVENT_AND_LISTENER_ID lookup on both databases.
-- When upgrading Modulith, diff against the official schema and add any new columns.
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
CREATE INDEX IF NOT EXISTS event_publication_by_listener_id_serialized_event_idx
    ON event_publication (listener_id, serialized_event);
