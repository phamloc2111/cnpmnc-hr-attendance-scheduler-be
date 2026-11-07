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

-- Preserve events uploaded before the SS-24 read model was introduced.
-- The default five-minute grace window matches ATTENDANCE_GRACE_MINUTES=5.
INSERT INTO attendance_records (
    id, employee_id, schedule_id, work_date, shift_name, check_type, recorded_at,
    photo_url, photo_captured_at, photo_location, device_location,
    device_latitude, device_longitude, status, verification_note
)
SELECT e.id, e.employee_id, e.schedule_id, s.work_date, s.shift_name, e.event_type,
       e.received_at,
       '/api/v1/attendance/events/' || e.id::text || '/photo',
       NULL, 'Chưa xác minh từ ảnh',
       e.latitude::text || ', ' || e.longitude::text,
       e.latitude, e.longitude,
       CASE
           WHEN e.event_type = 'CHECK_IN' AND e.received_at > s.starts_at + INTERVAL '5' MINUTE THEN 'LATE'
           WHEN e.event_type = 'CHECK_OUT' AND e.received_at < s.ends_at - INTERVAL '5' MINUTE THEN 'EARLY_LEAVE'
           ELSE 'ON_TIME'
       END,
       'Ảnh Timemark chưa được đối chiếu với vị trí GPS.'
FROM attendance_events e
JOIN work_schedules s ON s.id = e.schedule_id;
