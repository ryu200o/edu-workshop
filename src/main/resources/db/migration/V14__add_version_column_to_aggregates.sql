-- V14: Add optimistic-locking version column to the three mutable aggregates.
-- ADR 0015 (Strategy B): a @Version column is a persistence concern, tracked only by the JPA
-- entities/write adapters (the domain aggregate never carries a version). Hibernate increments it
-- on each UPDATE and uses it in the WHERE clause (version = ?); a 0-row match surfaces as
-- ObjectOptimisticLockingFailureException which the module ExceptionAdvices map to HTTP 409.
--
-- The column is BIGINT NOT NULL DEFAULT 0 so existing rows start at version 0 and every
-- subsequent write bumps it. Portability: H2 (PostgreSQL mode, tests) and PostgreSQL (runtime)
-- both accept ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0 for an existing table.

ALTER TABLE rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workshops ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE registrations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
