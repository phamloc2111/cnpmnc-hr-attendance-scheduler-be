CREATE TABLE attendance_explanations (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    category VARCHAR(32) NOT NULL,
    attendance_record_id UUID,
    reason VARCHAR(2000) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_reason VARCHAR(2000),
    exempt_late_penalty BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by UUID,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_attendance_explanation_target UNIQUE(schedule_id,category,attendance_record_id),
    CONSTRAINT ck_attendance_explanation_category CHECK(category IN ('LATE','MISSING_CHECK_IN','MISSING_CHECK_OUT','INVALID')),
    CONSTRAINT ck_attendance_explanation_status CHECK(status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_attendance_explanation_decision CHECK((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL) OR (status<>'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE INDEX ix_attendance_explanation_shift_kind ON attendance_explanations(schedule_id,category);
CREATE UNIQUE INDEX uq_attendance_explanation_invalid_record ON attendance_explanations(attendance_record_id);
CREATE INDEX ix_attendance_explanations_employee_date ON attendance_explanations(employee_id,work_date DESC);
CREATE INDEX ix_attendance_explanations_status_date ON attendance_explanations(status,submitted_at DESC);
