CREATE TABLE work_shift_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes INTEGER NOT NULL,
    created_by UUID NOT NULL,
    CONSTRAINT shift_different_times CHECK (start_time <> end_time),
    CONSTRAINT shift_break_valid CHECK (break_minutes >= 0 AND break_minutes < 1440)
);

CREATE TABLE work_schedules (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    shift_template_id UUID NOT NULL REFERENCES work_shift_templates(id),
    work_date DATE NOT NULL,
    shift_name VARCHAR(120) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    break_minutes INTEGER NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    CONSTRAINT schedule_positive_duration CHECK (ends_at > starts_at),
    CONSTRAINT schedule_break_valid CHECK (break_minutes >= 0),
    CONSTRAINT schedule_unique UNIQUE (employee_id, starts_at, ends_at)
);
CREATE INDEX idx_schedule_employee_date ON work_schedules(employee_id, work_date);
CREATE INDEX idx_schedule_date ON work_schedules(work_date);
