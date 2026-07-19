-- V3: Decouple the room name from its coordinates and turn the code into an independent integer.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
--
-- 1. code: VARCHAR(10) -> INTEGER (independent FE-ordering aid, no longer derived from name).
-- 2. Replace the global `uk_rooms_name` (name alone) with `uk_rooms_building_floor_name`
--    (building + floor + name) so two rooms in different buildings/floors may share a name, and
--    uniqueness is scoped to the physical location like `uk_rooms_building_floor_code`.
-- 3. Recreate `uk_rooms_building_floor_code` against the new INTEGER code type.

ALTER TABLE rooms ALTER COLUMN code SET DATA TYPE INTEGER USING code::INTEGER;

ALTER TABLE rooms DROP CONSTRAINT uk_rooms_name;

ALTER TABLE rooms DROP CONSTRAINT uk_rooms_building_floor_code;
ALTER TABLE rooms ADD CONSTRAINT uk_rooms_building_floor_code UNIQUE (building, floor, code);

ALTER TABLE rooms ADD CONSTRAINT uk_rooms_building_floor_name UNIQUE (building, floor, name);
