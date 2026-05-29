-- =========================================================
-- KongKongFarm PostgreSQL DDL
-- Version: ERDCloud final + core schema + crop repayment policy + BNPL request + ledger interest due day
-- 목적:
-- 1. 외부 식별자인 public_id(UUID)를 FK처럼 사용하는 초기 설계안
-- 2. 이후 BIGINT FK 구조와 조인 성능, 인덱스 크기, P95/P99 비교용
-- =========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS core;
SET search_path TO core;

-- =========================================================
-- 1. Independent master tables
-- =========================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    resident_id_hash VARCHAR(255) NOT NULL,
    resident_id_enc VARCHAR(100),

    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255),
    zip_code VARCHAR(10) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,

    role VARCHAR(20) NOT NULL
        CHECK (role IN ('SUPER_ADMIN', 'REVIEWER', 'VIEWER')),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    last_login_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE crop_repayment_policies (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    crop_type VARCHAR(30) NOT NULL UNIQUE
        CHECK (crop_type IN ('RICE', 'PEPPER', 'SOYBEAN', 'GARLIC', 'ONION')),

    crop_name VARCHAR(50) NOT NULL,

    harvest_start_month INTEGER NOT NULL
        CHECK (harvest_start_month BETWEEN 1 AND 12),
    harvest_end_month INTEGER NOT NULL
        CHECK (harvest_end_month BETWEEN 1 AND 12),

    sales_start_month INTEGER NOT NULL
        CHECK (sales_start_month BETWEEN 1 AND 12),
    sales_end_month INTEGER NOT NULL
        CHECK (sales_end_month BETWEEN 1 AND 12),

    repayment_due_month INTEGER NOT NULL
        CHECK (repayment_due_month BETWEEN 1 AND 12),
    repayment_due_day INTEGER NOT NULL
        CHECK (repayment_due_day BETWEEN 1 AND 31),

    description VARCHAR(500),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO crop_repayment_policies (
    crop_type,
    crop_name,
    harvest_start_month,
    harvest_end_month,
    sales_start_month,
    sales_end_month,
    repayment_due_month,
    repayment_due_day,
    description
) VALUES
    ('RICE', '벼', 10, 11, 10, 12, 1, 31, '수확 및 판매 이후 이듬해 1월 말 상환'),
    ('PEPPER', '고추', 8, 10, 9, 11, 12, 31, '건조 및 지속 판매 이후 12월 말 상환'),
    ('SOYBEAN', '콩', 10, 11, 11, 12, 1, 31, '정부 수매 및 판매 이후 이듬해 1월 말 상환'),
    ('GARLIC', '마늘', 5, 6, 6, 8, 9, 30, '건조 후 출하 이후 9월 말 상환'),
    ('ONION', '양파', 5, 6, 6, 7, 8, 31, '수확 직후 집중 출하 이후 8월 말 상환')
ON CONFLICT (crop_type) DO UPDATE SET
    crop_name = EXCLUDED.crop_name,
    harvest_start_month = EXCLUDED.harvest_start_month,
    harvest_end_month = EXCLUDED.harvest_end_month,
    sales_start_month = EXCLUDED.sales_start_month,
    sales_end_month = EXCLUDED.sales_end_month,
    repayment_due_month = EXCLUDED.repayment_due_month,
    repayment_due_day = EXCLUDED.repayment_due_day,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- 2. User / farmer domain
-- =========================================================

CREATE TABLE user_auth (
    id BIGSERIAL PRIMARY KEY,

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES users(public_id),

    pin_hash VARCHAR(255),
    password_hash VARCHAR(255),
    refresh_token VARCHAR(500),

    pin_changed_at TIMESTAMP,
    last_login_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE farmer_profiles (
    id BIGSERIAL PRIMARY KEY,

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES users(public_id),

    farm_address VARCHAR(255) NOT NULL,
    farm_address_detail VARCHAR(255),
    farm_zip_code VARCHAR(10) NOT NULL,

    field_area_m2 NUMERIC(12,2) NOT NULL CHECK (field_area_m2 > 0),
    main_crop VARCHAR(50) NOT NULL,

    has_crop_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    farming_since INTEGER NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. Credit application / scoring domain
-- =========================================================

CREATE TABLE credit_limit_applications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    reviewed_by_admin_public_id UUID
        REFERENCES admin_users(public_id) ON DELETE SET NULL,

    requested_amount NUMERIC(15,2) NOT NULL CHECK (requested_amount > 0),
    approved_amount NUMERIC(15,2)
        CHECK (approved_amount IS NULL OR approved_amount >= 0),

    is_reapplication BOOLEAN NOT NULL DEFAULT FALSE,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

    rejection_reason VARCHAR(500),

    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        (status = 'APPROVED' AND approved_amount IS NOT NULL)
        OR (status <> 'APPROVED')
    )
);

CREATE TABLE farmer_documents (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    application_public_id UUID
        REFERENCES credit_limit_applications(public_id) ON DELETE SET NULL,

    document_type VARCHAR(30) NOT NULL
        CHECK (document_type IN ('FARM_MANAGEMENT', 'CROP_INSURANCE')),

    file_url VARCHAR(500) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ass_scores (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    application_public_id UUID NOT NULL UNIQUE
        REFERENCES credit_limit_applications(public_id),

    estimated_income NUMERIC(15,2) NOT NULL CHECK (estimated_income >= 0),
    price_snapshot_date DATE NOT NULL,

    income_score INTEGER NOT NULL CHECK (income_score BETWEEN 0 AND 60),
    insurance_score INTEGER NOT NULL CHECK (insurance_score BETWEEN 0 AND 25),
    farming_career_score INTEGER NOT NULL CHECK (farming_career_score BETWEEN 0 AND 15),
    total_score INTEGER NOT NULL CHECK (total_score BETWEEN 0 AND 100),

    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bss_scores (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    application_public_id UUID
        REFERENCES credit_limit_applications(public_id) ON DELETE SET NULL,

    period_type VARCHAR(20) NOT NULL
        CHECK (period_type IN ('MONTHLY', 'ANNUAL')),

    period_year INTEGER NOT NULL,
    period_month INTEGER CHECK (period_month BETWEEN 1 AND 12),

    monthly_score INTEGER,
    annual_score INTEGER,
    total_score INTEGER NOT NULL,

    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        (period_type = 'MONTHLY' AND period_month IS NOT NULL)
        OR (period_type = 'ANNUAL' AND period_month IS NULL)
    )
);

-- =========================================================
-- 4. Product / catalog domain
-- =========================================================

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    category_public_id UUID NOT NULL
        REFERENCES categories(public_id),

    name VARCHAR(100) NOT NULL,
    description TEXT,

    price NUMERIC(15,2) NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),

    unit VARCHAR(20) NOT NULL,
    image_url VARCHAR(500),

    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'
        CHECK (status IN ('ON_SALE', 'OUT_OF_STOCK', 'HIDDEN')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    product_public_id UUID NOT NULL
        REFERENCES products(public_id),

    quantity INTEGER NOT NULL CHECK (quantity > 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cart_items_user_product
        UNIQUE (user_public_id, product_public_id)
);

-- =========================================================
-- 5. BNPL payment request / order domain
-- 결제요청 생성은 catalog에서 담당
-- 금융 검증, 한도 차감, 원장 반영은 core에서 담당
-- =========================================================

CREATE TABLE bnpl_payment_requests (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    total_amount NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0),

    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN (
            'REQUESTED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'CANCELLED',
            'CONFIRMED'
        )),

    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    rejected_reason VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bnpl_payment_request_items (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    payment_request_public_id UUID NOT NULL
        REFERENCES bnpl_payment_requests(public_id) ON DELETE CASCADE,

    product_public_id UUID NOT NULL
        REFERENCES products(public_id),

    product_name_snapshot VARCHAR(100) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL CHECK (unit_price_snapshot >= 0),

    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_price NUMERIC(15,2) NOT NULL CHECK (total_price >= 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    payment_request_public_id UUID UNIQUE
        REFERENCES bnpl_payment_requests(public_id),

    total_amount NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0),

    order_status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (order_status IN ('CREATED', 'CONFIRMED', 'CANCELLED')),

    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PREPARING'
        CHECK (delivery_status IN ('PREPARING', 'SHIPPED', 'DELIVERED')),

    recipient_name VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,

    delivery_address VARCHAR(255) NOT NULL,
    delivery_address_detail VARCHAR(255),
    delivery_zip_code VARCHAR(10) NOT NULL,

    ordered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    order_public_id UUID NOT NULL
        REFERENCES orders(public_id),

    product_public_id UUID NOT NULL
        REFERENCES products(public_id),

    product_name_snapshot VARCHAR(100) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL CHECK (unit_price_snapshot >= 0),

    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_price NUMERIC(15,2) NOT NULL CHECK (total_price >= 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 6. Credit limit / ledger domain
-- =========================================================

CREATE TABLE credit_limits (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    application_public_id UUID NOT NULL UNIQUE
        REFERENCES credit_limit_applications(public_id),

    -- 한도 승인 당시 대표 작물 스냅샷
    -- 프로필의 main_crop이 나중에 변경되어도 기존 한도 정책은 승인 당시 작물 기준으로 유지한다.
    crop_type_snapshot VARCHAR(30) NOT NULL
        REFERENCES crop_repayment_policies(crop_type),

    total_limit NUMERIC(15,2) NOT NULL CHECK (total_limit > 0),
    used_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (used_amount >= 0),

    interest_rate NUMERIC(6,4) NOT NULL CHECK (interest_rate >= 0),

    -- 매월 이자 자동 상환일, 1~28 범위
    -- 한도 승인 시 기본 정책: min(승인일 day + 10, 28)
    interest_due_day INTEGER NOT NULL DEFAULT 11
        CHECK (interest_due_day BETWEEN 1 AND 28),

    principal_due_date DATE NOT NULL,
    expires_at DATE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REPAID', 'EXPIRED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (used_amount <= total_limit)
);

CREATE TABLE credit_usage_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES credit_limits(public_id),

    order_public_id UUID
        REFERENCES orders(public_id),

    payment_request_public_id UUID
        REFERENCES bnpl_payment_requests(public_id),

    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),

    usage_type VARCHAR(20) NOT NULL
        CHECK (usage_type IN ('PURCHASE', 'CANCEL', 'ADJUSTMENT')),

    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        (usage_type = 'PURCHASE' AND order_public_id IS NOT NULL)
        OR (usage_type <> 'PURCHASE')
    )
);

CREATE TABLE interest_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES credit_limits(public_id),

    base_principal NUMERIC(15,2) NOT NULL CHECK (base_principal >= 0),


    due_date DATE NOT NULL,

    interest_amount NUMERIC(15,2) NOT NULL CHECK (interest_amount >= 0),
    amount_paid NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),

    paid_at TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'PAID', 'OVERDUE', 'PARTIAL')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_interest_ledger_credit_limit_due_date
        UNIQUE (credit_limit_public_id, due_date),

    CHECK (amount_paid <= interest_amount)
);

CREATE TABLE principal_repayment_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES credit_limits(public_id),

    order_public_id UUID NOT NULL UNIQUE
        REFERENCES orders(public_id),

    payment_request_public_id UUID
        REFERENCES bnpl_payment_requests(public_id),

    due_date DATE NOT NULL,

    principal_amount NUMERIC(15,2) NOT NULL CHECK (principal_amount >= 0),
    amount_paid NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),

    paid_at TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'PAID', 'OVERDUE', 'PARTIAL')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (amount_paid <= principal_amount)
);

CREATE TABLE payment_event_process_logs (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(80) NOT NULL UNIQUE,
    checkout_request_id UUID NOT NULL UNIQUE,
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_overdue_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    credit_limit_public_id UUID NOT NULL
        REFERENCES credit_limits(public_id),

    interest_ledger_public_id UUID
        REFERENCES interest_ledger(public_id),

    principal_repayment_public_id UUID
        REFERENCES principal_repayment_ledger(public_id),

    overdue_type VARCHAR(20) NOT NULL
        CHECK (overdue_type IN ('INTEREST', 'PRINCIPAL')),

    overdue_amount NUMERIC(15,2) NOT NULL CHECK (overdue_amount > 0),
    overdue_days INTEGER NOT NULL CHECK (overdue_days >= 0),

    stage VARCHAR(20) NOT NULL
        CHECK (stage IN ('STAGE1', 'STAGE2', 'STAGE3')),

    penalty_rate NUMERIC(6,4) NOT NULL CHECK (penalty_rate >= 0),
    penalty_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (penalty_amount >= 0),

    action_taken VARCHAR(30) NOT NULL
        CHECK (action_taken IN ('NOTIFICATION', 'LIMIT_SUSPENDED', 'LEGAL')),

    resolved_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        (overdue_type = 'INTEREST'
            AND interest_ledger_public_id IS NOT NULL
            AND principal_repayment_public_id IS NULL)
        OR
        (overdue_type = 'PRINCIPAL'
            AND principal_repayment_public_id IS NOT NULL
            AND interest_ledger_public_id IS NULL)
    )
);

-- =========================================================
-- 7. Wallet domain
-- =========================================================

CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES users(public_id),

    balance NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),

    deposit_bank_name VARCHAR(50) NOT NULL,
    deposit_account_number VARCHAR(50) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    wallet_public_id UUID NOT NULL
        REFERENCES wallets(public_id),

    transaction_type VARCHAR(30) NOT NULL
        CHECK (transaction_type IN (
            'DEPOSIT',
            'INTEREST_PAYMENT',
            'PRINCIPAL_PAYMENT',
            'REFUND',
            'ADJUSTMENT'
        )),

    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    balance_after NUMERIC(15,2) NOT NULL CHECK (balance_after >= 0),

    related_type VARCHAR(50),
    related_public_id UUID,

    description VARCHAR(500),

    transacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 8. Notification / audit domain
-- =========================================================

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES users(public_id),

    title VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NOT NULL,

    notification_type VARCHAR(30) NOT NULL
        CHECK (notification_type IN (
            'LIMIT_APPROVED',
            'LIMIT_REJECTED',
            'INTEREST_DUE',
            'PRINCIPAL_DUE',
            'OVERDUE',
            'ORDER_CONFIRMED',
            'BNPL_REQUESTED',
            'BNPL_APPROVED',
            'BNPL_REJECTED'
        )),

    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    admin_user_public_id UUID
        REFERENCES admin_users(public_id),

    user_public_id UUID
        REFERENCES users(public_id),

    action VARCHAR(30) NOT NULL
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'SUSPEND')),

    target_table VARCHAR(100) NOT NULL,
    target_public_id UUID,

    before_data JSONB,
    after_data JSONB,

    ip_address VARCHAR(45),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 9. Helpful indexes for UUID reference experiment
-- =========================================================

-- user relation indexes
CREATE INDEX idx_user_auth_user_public_id
    ON user_auth(user_public_id);

CREATE INDEX idx_wallets_user_public_id
    ON wallets(user_public_id);

CREATE INDEX idx_farmer_profiles_user_public_id
    ON farmer_profiles(user_public_id);

CREATE INDEX idx_farmer_documents_user_public_id
    ON farmer_documents(user_public_id);

CREATE INDEX idx_credit_limit_applications_user_public_id
    ON credit_limit_applications(user_public_id);

CREATE INDEX idx_ass_scores_user_public_id
    ON ass_scores(user_public_id);

CREATE INDEX idx_bss_scores_user_public_id
    ON bss_scores(user_public_id);

CREATE INDEX idx_cart_items_user_public_id
    ON cart_items(user_public_id);

CREATE INDEX idx_orders_user_public_id
    ON orders(user_public_id);

CREATE INDEX idx_credit_limits_user_public_id
    ON credit_limits(user_public_id);

CREATE INDEX idx_credit_limits_crop_type_snapshot
    ON credit_limits(crop_type_snapshot);

CREATE INDEX idx_loan_overdue_ledger_user_public_id
    ON loan_overdue_ledger(user_public_id);

CREATE INDEX idx_notifications_user_public_id
    ON notifications(user_public_id);

-- crop policy indexes
CREATE INDEX idx_crop_repayment_policies_crop_type
    ON crop_repayment_policies(crop_type);

-- product / catalog indexes
CREATE INDEX idx_products_category_public_id
    ON products(category_public_id);

CREATE INDEX idx_cart_items_product_public_id
    ON cart_items(product_public_id);

-- application / scoring indexes
CREATE INDEX idx_farmer_documents_application_public_id
    ON farmer_documents(application_public_id);

CREATE INDEX idx_ass_scores_application_public_id
    ON ass_scores(application_public_id);

CREATE INDEX idx_bss_scores_application_public_id
    ON bss_scores(application_public_id);

CREATE INDEX idx_credit_limits_application_public_id
    ON credit_limits(application_public_id);

-- payment / order indexes
CREATE INDEX idx_bnpl_payment_requests_user_public_id
    ON bnpl_payment_requests(user_public_id);

CREATE INDEX idx_bnpl_payment_requests_status
    ON bnpl_payment_requests(status);

CREATE INDEX idx_bnpl_payment_request_items_request_public_id
    ON bnpl_payment_request_items(payment_request_public_id);

CREATE INDEX idx_bnpl_payment_request_items_product_public_id
    ON bnpl_payment_request_items(product_public_id);

CREATE INDEX idx_orders_payment_request_public_id
    ON orders(payment_request_public_id);

CREATE INDEX idx_order_items_order_public_id
    ON order_items(order_public_id);

CREATE INDEX idx_order_items_product_public_id
    ON order_items(product_public_id);

-- ledger indexes
CREATE INDEX idx_credit_usage_ledger_credit_limit_public_id
    ON credit_usage_ledger(credit_limit_public_id);

CREATE INDEX idx_credit_usage_ledger_order_public_id
    ON credit_usage_ledger(order_public_id);

CREATE INDEX idx_credit_usage_ledger_payment_request_public_id
    ON credit_usage_ledger(payment_request_public_id);

CREATE INDEX idx_interest_ledger_credit_limit_public_id
    ON interest_ledger(credit_limit_public_id);

CREATE INDEX idx_interest_ledger_due_date
    ON interest_ledger(due_date);

CREATE INDEX idx_principal_repayment_ledger_credit_limit_public_id
    ON principal_repayment_ledger(credit_limit_public_id);

CREATE INDEX idx_principal_repayment_ledger_order_public_id
    ON principal_repayment_ledger(order_public_id);

CREATE INDEX idx_principal_repayment_ledger_payment_request_public_id
    ON principal_repayment_ledger(payment_request_public_id);

CREATE INDEX idx_loan_overdue_ledger_credit_limit_public_id
    ON loan_overdue_ledger(credit_limit_public_id);

CREATE INDEX idx_loan_overdue_ledger_interest_public_id
    ON loan_overdue_ledger(interest_ledger_public_id);

CREATE INDEX idx_loan_overdue_ledger_principal_public_id
    ON loan_overdue_ledger(principal_repayment_public_id);

-- wallet indexes
CREATE INDEX idx_wallet_transactions_wallet_public_id
    ON wallet_transactions(wallet_public_id);

-- audit indexes
CREATE INDEX idx_audit_logs_admin_user_public_id
    ON audit_logs(admin_user_public_id);

CREATE INDEX idx_audit_logs_user_public_id
    ON audit_logs(user_public_id);

CREATE INDEX idx_audit_logs_target_public_id
    ON audit_logs(target_public_id);
