CREATE TABLE diligence_day_reviews (
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    absence VARCHAR(32) NOT NULL,
    early_leave_approved BOOLEAN NOT NULL,
    missing VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    revision BIGINT NOT NULL,
    reviewed_by UUID NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(employee_id, work_date),
    CONSTRAINT ck_diligence_absence CHECK(absence IN ('AUTO','APPROVED_LEAVE','LATE_APPROVED','UNEXCUSED')),
    CONSTRAINT ck_diligence_missing CHECK(missing IN ('AUTO','TIMELY_EXPLANATION','LATE_EXPLANATION','SYSTEM_CONFIRMED'))
);
CREATE TABLE diligence_review_audit (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    revision BIGINT NOT NULL,
    reviewed_by UUID NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot VARCHAR(4000) NOT NULL,
    CONSTRAINT uq_diligence_review_audit UNIQUE(employee_id,work_date,revision)
);
