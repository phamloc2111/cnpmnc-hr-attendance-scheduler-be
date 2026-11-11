CREATE TABLE priority_weights (
    manager_id UUID PRIMARY KEY REFERENCES identity_accounts(id) ON DELETE CASCADE,
    punctuality SMALLINT NOT NULL CHECK (punctuality BETWEEN 0 AND 100),
    hour_balance SMALLINT NOT NULL CHECK (hour_balance BETWEEN 0 AND 100),
    tenure SMALLINT NOT NULL CHECK (tenure BETWEEN 0 AND 100),
    position_match SMALLINT NOT NULL CHECK (position_match BETWEEN 0 AND 100),
    manager_rating SMALLINT NOT NULL CHECK (manager_rating BETWEEN 0 AND 100),
    updated_by UUID NOT NULL REFERENCES identity_accounts(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT priority_weights_sum CHECK (punctuality + hour_balance + tenure + position_match + manager_rating = 100)
);
CREATE TABLE priority_ratings (
    manager_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    updated_by UUID NOT NULL REFERENCES identity_accounts(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (manager_id, employee_id)
);
CREATE INDEX priority_ratings_employee_idx ON priority_ratings(employee_id);
