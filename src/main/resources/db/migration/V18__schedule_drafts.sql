ALTER TABLE work_schedules ADD COLUMN draft BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_schedule_draft_date ON work_schedules(draft, work_date);
