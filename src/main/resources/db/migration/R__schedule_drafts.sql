ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS draft BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_schedule_draft_date ON work_schedules(draft, work_date);
