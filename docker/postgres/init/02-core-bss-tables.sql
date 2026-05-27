CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- core 스키마 비즈니스 테이블 (users/user_auth는 01-auth-tables.sql 참조)
-- =========================================================

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

