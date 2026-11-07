CREATE TABLE attendance_records (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    schedule_id UUID,
    work_date DATE NOT NULL,
    shift_name VARCHAR(120),
    check_type VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    photo_url VARCHAR(2048) NOT NULL,
    photo_captured_at TIMESTAMP WITH TIME ZONE,
    photo_location VARCHAR(500) NOT NULL,
    device_location VARCHAR(500) NOT NULL,
    device_latitude DOUBLE PRECISION,
    device_longitude DOUBLE PRECISION,
    status VARCHAR(30) NOT NULL,
    verification_note VARCHAR(500),
    CONSTRAINT attendance_check_type_valid CHECK (check_type IN ('CHECK_IN', 'CHECK_OUT')),
    CONSTRAINT attendance_status_valid CHECK (status IN ('ON_TIME', 'LATE', 'EARLY_LEAVE', 'ABSENT', 'REJECTED'))
);
CREATE INDEX idx_attendance_employee_date ON attendance_records(employee_id, work_date);
CREATE INDEX idx_attendance_work_date ON attendance_records(work_date);
