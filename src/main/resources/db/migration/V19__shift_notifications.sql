CREATE TABLE shift_reminder_deliveries (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL,
    kind VARCHAR(32) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_shift_reminder UNIQUE (schedule_id, employee_id, kind)
);

CREATE INDEX idx_shift_reminder_deliveries ON shift_reminder_deliveries(schedule_id, kind);

CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    reference_id UUID,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_logs_employee ON notification_logs(employee_id, sent_at DESC);
