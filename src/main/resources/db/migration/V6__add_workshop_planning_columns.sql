ALTER TABLE workshops
ADD COLUMN room_capacity_snapshot INTEGER,
ADD COLUMN has_room_warning BOOLEAN NOT NULL DEFAULT FALSE;
