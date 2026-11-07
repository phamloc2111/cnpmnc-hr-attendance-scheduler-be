CREATE TABLE attendance_events (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES work_schedules(id),
    employee_id UUID NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    located_at TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters DOUBLE PRECISION NOT NULL,
    photo_content_type VARCHAR(32) NOT NULL,
    photo_bytes BYTEA NOT NULL,
    CONSTRAINT attendance_event_type CHECK (event_type IN ('CHECK_IN', 'CHECK_OUT')),
    CONSTRAINT attendance_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT attendance_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT attendance_accuracy CHECK (accuracy_meters >= 0 AND accuracy_meters <= 10000),
    CONSTRAINT attendance_once_per_type UNIQUE (schedule_id, event_type)
);
CREATE INDEX idx_attendance_schedule ON attendance_events(schedule_id);
