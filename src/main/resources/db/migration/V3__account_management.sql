ALTER TABLE identity_accounts ADD COLUMN position VARCHAR(120);
ALTER TABLE identity_accounts ADD COLUMN manager_id UUID REFERENCES identity_accounts(id);
ALTER TABLE identity_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_identity_accounts_manager_role ON identity_accounts(manager_id, role);
CREATE INDEX idx_identity_accounts_role_locked ON identity_accounts(role, locked);
CREATE TABLE identity_account_skills (
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    skill_order INTEGER NOT NULL,
    skill VARCHAR(80) NOT NULL,
    PRIMARY KEY (account_id, skill_order)
);
