CREATE TABLE IF NOT EXISTS schedule_draft_weeks (
    id UUID PRIMARY KEY,
    manager_id UUID NOT NULL,
    week_start DATE NOT NULL,
    CONSTRAINT uq_schedule_draft_week UNIQUE (manager_id, week_start)
);
