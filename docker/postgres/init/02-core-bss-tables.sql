CREATE TABLE IF NOT EXISTS core.credit_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT,
    total_limit NUMERIC(15, 2) NOT NULL,
    used_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.interest_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credit_limit_id BIGINT NOT NULL,
    interest_amount NUMERIC(15, 2) NOT NULL,
    amount_paid NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20),
    due_date DATE,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.principal_repayment_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credit_limit_id BIGINT NOT NULL,
    principal_amount NUMERIC(15, 2) NOT NULL,
    amount_paid NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20),
    due_date DATE,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.loan_overdue_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credit_limit_id BIGINT NOT NULL,
    overdue_amount NUMERIC(15, 2) NOT NULL,
    overdue_days INT NOT NULL DEFAULT 0,
    stage VARCHAR(20),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.bss_scores (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT,
    period_type VARCHAR(20) NOT NULL,
    period_year INT NOT NULL,
    period_month INT,
    monthly_score INT,
    annual_score INT,
    total_score INT NOT NULL,
    calculated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
