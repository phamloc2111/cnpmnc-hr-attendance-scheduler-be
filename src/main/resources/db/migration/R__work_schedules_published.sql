ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS published_by UUID;

CREATE INDEX IF NOT EXISTS idx_schedule_published ON work_schedules(employee_id, published, work_date);
