CREATE TABLE shift_passes (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    requester_id UUID NOT NULL,
    claimer_id UUID,
    work_date DATE NOT NULL,
    shift_name VARCHAR(120) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    break_minutes INTEGER NOT NULL DEFAULT 0,
    required_position_id UUID,
    required_position VARCHAR(120),
    reason VARCHAR(500),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'CLAIMED', 'CANCELLED', 'EXPIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_shift_passes_open_schedule
    ON shift_passes(schedule_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_shift_passes_status_starts
    ON shift_passes(status, starts_at);

CREATE INDEX idx_shift_passes_requester
    ON shift_passes(requester_id, created_at DESC);

CREATE TABLE shift_pass_notifications (
    id UUID PRIMARY KEY,
    shift_pass_id UUID NOT NULL REFERENCES shift_passes(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL,
    recipient_role VARCHAR(20) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'CLOSED', 'READ')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_shift_pass_notif_recipient
    ON shift_pass_notifications(recipient_id, status, created_at DESC);

CREATE INDEX idx_shift_pass_notif_pass
    ON shift_pass_notifications(shift_pass_id);
