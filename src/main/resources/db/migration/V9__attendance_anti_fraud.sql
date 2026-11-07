ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS photo_hash VARCHAR(128);
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS device_id VARCHAR(128);
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS device_name VARCHAR(160);
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS mock_location BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS fraud_type VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_attendance_photo_hash ON attendance_records(photo_hash);

CREATE TABLE IF NOT EXISTS attendance_registered_devices (
    employee_id UUID PRIMARY KEY,
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(160),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_by UUID
);
