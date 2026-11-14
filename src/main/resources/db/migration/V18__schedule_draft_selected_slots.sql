-- Repeatable draft-week migration runs after versioned migrations on a fresh database.
CREATE TABLE IF NOT EXISTS schedule_draft_weeks (
    id UUID PRIMARY KEY,
    manager_id UUID NOT NULL,
    week_start DATE NOT NULL,
    CONSTRAINT uq_schedule_draft_week UNIQUE (manager_id, week_start)
);

CREATE TABLE schedule_draft_selected_slots (
    manager_id UUID NOT NULL,
    week_start DATE NOT NULL,
    work_date DATE NOT NULL,
    shift_template_id UUID NOT NULL,
    PRIMARY KEY (manager_id, week_start, work_date, shift_template_id),
    CONSTRAINT fk_draft_selected_week FOREIGN KEY (manager_id, week_start)
        REFERENCES schedule_draft_weeks(manager_id, week_start) ON DELETE CASCADE
);
