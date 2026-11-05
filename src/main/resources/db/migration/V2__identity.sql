CREATE TABLE identity_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT identity_email_normalized CHECK (email = LOWER(TRIM(email)))
);

CREATE TABLE identity_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_identity_sessions_account ON identity_sessions(account_id);
CREATE INDEX idx_identity_sessions_expiry ON identity_sessions(expires_at);
