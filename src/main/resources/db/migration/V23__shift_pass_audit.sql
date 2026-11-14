CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    target_employee_id UUID,
    target_date DATE NOT NULL,
    category VARCHAR(32) NOT NULL CHECK (category IN ('SCHEDULE', 'ATTENDANCE')),
    action VARCHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_occurred_at ON audit_logs(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_date ON audit_logs(target_date DESC);
