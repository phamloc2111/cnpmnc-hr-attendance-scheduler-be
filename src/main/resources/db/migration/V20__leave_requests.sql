CREATE TABLE leave_requests (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    schedule_id UUID REFERENCES work_schedules(id) ON DELETE SET NULL,
    work_date DATE NOT NULL,
    shift_template_id UUID REFERENCES work_shift_templates(id) ON DELETE SET NULL,
    shift_name VARCHAR(120) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    reviewer_id UUID,
    reviewer_note TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_leave_requests_employee ON leave_requests(employee_id, work_date);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);

ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS understaffed BOOLEAN NOT NULL DEFAULT FALSE;
