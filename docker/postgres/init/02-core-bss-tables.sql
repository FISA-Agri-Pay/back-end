CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS core;

-- =========================================================
-- Local core base tables for service-core / service-batch tests
-- =========================================================

CREATE TABLE IF NOT EXISTS core.users (
                                          id BIGSERIAL PRIMARY KEY,
                                          public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL DEFAULT '테스트사용자',
    phone VARCHAR(20) NOT NULL DEFAULT '01000000000',
    resident_id_hash VARCHAR(255) NOT NULL DEFAULT 'local-hash',
    address VARCHAR(255) NOT NULL DEFAULT 'local-address',
    address_detail VARCHAR(255),
    zip_code VARCHAR(10) NOT NULL DEFAULT '00000',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS core.credit_limit_applications (
                                                              id BIGSERIAL PRIMARY KEY,
                                                              public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    reviewed_by BIGINT,
    requested_amount NUMERIC(15, 2),
    approved_amount NUMERIC(15, 2),
    is_reapplication BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    applied_at TIMESTAMP DEFAULT now(),
    decided_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS core.farmer_profiles (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    user_id BIGINT NOT NULL,
                                                    farm_address VARCHAR(255) NOT NULL DEFAULT 'local-farm-address',
    farm_address_detail VARCHAR(255),
    farm_zip_code VARCHAR(10) NOT NULL DEFAULT '00000',
    field_area_m2 NUMERIC(12, 2) NOT NULL,
    main_crop VARCHAR(50) NOT NULL,
    has_crop_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    farming_since INTEGER NOT NULL DEFAULT 2024,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS core.farmer_documents (
                                                     id BIGSERIAL PRIMARY KEY,
                                                     user_id BIGINT NOT NULL,
                                                     application_id BIGINT,
                                                     document_type VARCHAR(30) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT now(),
    created_at TIMESTAMP DEFAULT now()
    );

-- =========================================================
-- BSS local test tables
-- =========================================================

CREATE TABLE IF NOT EXISTS core.crop_repayment_policies (
                                                            crop_type VARCHAR(30) PRIMARY KEY,
                                                            crop_name VARCHAR(50) NOT NULL,
                                                            repayment_month INTEGER NOT NULL,
                                                            repayment_day INTEGER NOT NULL,
                                                            created_at TIMESTAMP DEFAULT now(),
                                                            updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_crop_repayment_month
    CHECK (repayment_month BETWEEN 1 AND 12),
    CONSTRAINT chk_crop_repayment_day
    CHECK (repayment_day BETWEEN 1 AND 31)
    );

INSERT INTO core.crop_repayment_policies (
    crop_type,
    crop_name,
    repayment_month,
    repayment_day
)
VALUES
    ('RICE', '쌀', 12, 31)
ON CONFLICT (crop_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS core.credit_limits (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  user_id BIGINT NOT NULL,
                                                  application_id BIGINT,
                                                  crop_type_snapshot VARCHAR(30) NOT NULL DEFAULT 'RICE',
                                                  total_limit NUMERIC(15, 2) NOT NULL,
    used_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    interest_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0350,
    interest_due_day INTEGER NOT NULL DEFAULT 11,
    principal_due_date DATE NOT NULL DEFAULT (current_date + interval '330 days'),
    expires_at DATE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT fk_credit_limits_crop_type_snapshot
    FOREIGN KEY (crop_type_snapshot)
    REFERENCES core.crop_repayment_policies(crop_type),
    CONSTRAINT chk_credit_limits_interest_due_day
    CHECK (interest_due_day BETWEEN 1 AND 28),
    CONSTRAINT chk_credit_limits_interest_rate
    CHECK (interest_rate >= 0),
    CONSTRAINT chk_credit_limits_used_amount
    CHECK (used_amount <= total_limit)
    );

CREATE TABLE IF NOT EXISTS core.credit_usage_ledger (
                                                        id BIGSERIAL PRIMARY KEY,
                                                        credit_limit_id BIGINT NOT NULL,
                                                        order_id BIGINT,
                                                        amount NUMERIC(15, 2) NOT NULL,
    usage_type VARCHAR(20) NOT NULL,
    used_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_credit_usage_ledger_type
    CHECK (usage_type IN ('PURCHASE', 'CANCEL', 'ADJUSTMENT')),
    CONSTRAINT chk_credit_usage_ledger_amount
    CHECK (amount > 0)
    );

CREATE TABLE IF NOT EXISTS core.interest_ledger (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    credit_limit_id BIGINT NOT NULL,
                                                    base_principal NUMERIC(15, 2) NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
                                                    interest_amount NUMERIC(15, 2) NOT NULL,
    amount_paid NUMERIC(15, 2) NOT NULL DEFAULT 0,
    paid_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS core.principal_repayment_ledger (
                                                               id BIGSERIAL PRIMARY KEY,
                                                               credit_limit_id BIGINT NOT NULL,
                                                               due_date DATE NOT NULL,
                                                               principal_amount NUMERIC(15, 2) NOT NULL,
    amount_paid NUMERIC(15, 2) NOT NULL DEFAULT 0,
    paid_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
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

CREATE TABLE IF NOT EXISTS core.payment_event_process_logs (
                                                               id BIGSERIAL PRIMARY KEY,
                                                               event_id VARCHAR(80) NOT NULL,
    checkout_request_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT uk_payment_event_process_logs_event_id UNIQUE (event_id),
    CONSTRAINT uk_payment_event_process_logs_checkout_request_id UNIQUE (checkout_request_id)
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
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_bss_period_type
    CHECK (period_type IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT chk_bss_period_month
    CHECK (
(period_type = 'MONTHLY' AND period_month BETWEEN 1 AND 12)
    OR (period_type = 'ANNUAL' AND period_month IS NULL)
    ),
    CONSTRAINT chk_bss_scores_range
    CHECK (
              total_score BETWEEN 0 AND 100
              AND (monthly_score IS NULL OR monthly_score BETWEEN 0 AND 100)
    AND (annual_score IS NULL OR annual_score BETWEEN 0 AND 100)
    )
    );

-- =========================================================
-- ASS local test tables
-- =========================================================

CREATE TABLE IF NOT EXISTS core.ass_scores (
                                               id BIGSERIAL PRIMARY KEY,
                                               user_id BIGINT NOT NULL,
                                               application_id BIGINT NOT NULL,
                                               estimated_income NUMERIC(15, 2) NOT NULL,
    price_snapshot_date DATE NOT NULL,
    income_score INTEGER NOT NULL,
    insurance_score INTEGER NOT NULL,
    farming_career_score INTEGER NOT NULL,
    total_score INTEGER NOT NULL,
    calculated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_ass_scores_application
    UNIQUE (application_id),
    CONSTRAINT chk_ass_scores_range
    CHECK (
              income_score BETWEEN 0 AND 60
              AND insurance_score BETWEEN 0 AND 25
              AND farming_career_score BETWEEN 0 AND 15
              AND total_score BETWEEN 0 AND 100
          ),
    CONSTRAINT chk_ass_total_score_consistency
    CHECK (total_score = income_score + insurance_score + farming_career_score)
    );

