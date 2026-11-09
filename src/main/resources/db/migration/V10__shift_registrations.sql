-- Reconstructed from the existing public schema on 2026-11-09 after the original file was lost.
-- Existing databases with checksum -1798121770 require the reviewed one-time recovery procedure.
CREATE TABLE shift_registrations (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    week_start DATE NOT NULL,
    shift_template_id UUID NOT NULL REFERENCES work_shift_templates(id),
    CONSTRAINT ck_shift_registration_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT uq_shift_registration UNIQUE (employee_id, week_start, shift_template_id)
);
CREATE INDEX idx_shift_registrations_employee_week ON shift_registrations(employee_id, week_start);

CREATE TABLE registration_reopens (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    week_start DATE NOT NULL,
    reopened_by UUID NOT NULL,
    reopened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_registration_reopen_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT uq_registration_reopen UNIQUE (employee_id, week_start)
);
