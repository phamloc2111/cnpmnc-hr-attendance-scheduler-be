CREATE TABLE notification_devices (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    session_id UUID NOT NULL,
    token TEXT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notification_devices_employee ON notification_devices(employee_id);
CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES notification_devices(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK(kind IN ('OPEN', 'REMINDER')),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    lease_token UUID,
    error_code VARCHAR(60),
    CONSTRAINT uq_notification_delivery UNIQUE(device_id, week_start, kind)
);
CREATE INDEX idx_notification_deliveries_pending ON notification_deliveries(status,next_attempt_at);
