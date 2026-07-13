-- V2: Relax the room-code constraint to a flexible 1–10 character alphanumeric code.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE rooms ALTER COLUMN code SET DATA TYPE VARCHAR(10);
