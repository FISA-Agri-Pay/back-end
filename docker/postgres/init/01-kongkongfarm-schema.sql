-- =========================================================
-- KongKongFarm PostgreSQL Init Schema
-- Version: local/dev schema aligned with latest ERD
--
-- 기준:
-- 1. local/dev는 단일 PostgreSQL 인스턴스를 사용한다.
-- 2. service-core, service-admin, service-batch 소유 테이블은 core schema에 둔다.
-- 3. service-catalog 소유 테이블은 catalog schema에 둔다.
-- 4. 내부 PK는 BIGSERIAL id를 사용한다.
-- 5. 외부 API, Kafka, Redis, 서비스 간 참조는 public_id(UUID)를 사용한다.
-- 6. 서비스 경계를 넘는 참조는 DB FK를 강제하지 않고 UUID 값과 스냅샷으로 관리한다.
-- =========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS catalog;

-- =========================================================
-- 1. users
-- =========================================================

CREATE TABLE IF NOT EXISTS core.users (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL UNIQUE,

    resident_id_hash VARCHAR(255) NOT NULL,
    resident_id_enc VARCHAR(500),

    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255),
    zip_code VARCHAR(20) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED', 'SUSPENDED', 'WITHDRAWN')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 2. user_auth
-- =========================================================

CREATE TABLE IF NOT EXISTS core.user_auth (
    id BIGSERIAL PRIMARY KEY,

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES core.users(public_id),

    pin_hash VARCHAR(255) ,
    password_hash VARCHAR(255),
    refresh_token TEXT,

    pin_changed_at TIMESTAMP,
    last_login_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. wallets
-- =========================================================

CREATE TABLE IF NOT EXISTS core.wallets (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES core.users(public_id),

    balance NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),

    deposit_bank_name VARCHAR(50) NOT NULL,
    deposit_account_number VARCHAR(50) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 4. wallet_transactions
-- =========================================================

CREATE TABLE IF NOT EXISTS core.wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    wallet_public_id UUID NOT NULL
        REFERENCES core.wallets(public_id),

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

    description TEXT,

    transacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 5. farmer_profiles
-- =========================================================

CREATE TABLE IF NOT EXISTS core.farmer_profiles (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL UNIQUE
        REFERENCES core.users(public_id),

    farm_address VARCHAR(255) NOT NULL,
    farm_address_detail VARCHAR(255),
    farm_zip_code VARCHAR(20) NOT NULL,

    field_area_m2 NUMERIC(12,2) NOT NULL CHECK (field_area_m2 > 0),
    main_crop VARCHAR(30) NOT NULL
        CHECK (main_crop IN ('RICE', 'PEPPER', 'SOYBEAN', 'GARLIC', 'ONION')),

    has_crop_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    farming_since INTEGER NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 7. admin_users
-- credit_limit_applications에서 참조하므로 먼저 생성한다.
-- =========================================================

CREATE TABLE IF NOT EXISTS core.admin_users (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN'
        CHECK (role IN ('ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    refresh_token VARCHAR(64),
    last_login_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Existing DB migration only. New installations already include this column in CREATE TABLE above.
ALTER TABLE IF EXISTS core.admin_users
    ADD COLUMN IF NOT EXISTS refresh_token VARCHAR(64);

-- =========================================================
-- 8. credit_limit_applications
-- =========================================================

CREATE TABLE IF NOT EXISTS core.credit_limit_applications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    reviewed_by_admin_public_id UUID
        REFERENCES core.admin_users(public_id),

    requested_amount NUMERIC(15,2) NOT NULL CHECK (requested_amount > 0),
    approved_amount NUMERIC(15,2) CHECK (approved_amount IS NULL OR approved_amount >= 0),

    is_reapplication BOOLEAN NOT NULL DEFAULT FALSE,

    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),

    rejection_reason TEXT,

    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 6. farmer_documents
-- =========================================================

CREATE TABLE IF NOT EXISTS core.farmer_documents (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    application_public_id UUID
        REFERENCES core.credit_limit_applications(public_id) ON DELETE SET NULL,

    document_type VARCHAR(50) NOT NULL,
    file_url TEXT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 9. ass_scores
-- =========================================================

CREATE TABLE IF NOT EXISTS core.ass_scores (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    application_public_id UUID NOT NULL UNIQUE
        REFERENCES core.credit_limit_applications(public_id),

    estimated_income NUMERIC(15,2) NOT NULL CHECK (estimated_income >= 0),
    price_snapshot_date DATE NOT NULL,

    income_score INTEGER NOT NULL CHECK (income_score BETWEEN 0 AND 60),
    insurance_score INTEGER NOT NULL CHECK (insurance_score BETWEEN 0 AND 25),
    farming_career_score INTEGER NOT NULL CHECK (farming_career_score BETWEEN 0 AND 15),
    total_score INTEGER NOT NULL CHECK (total_score BETWEEN 0 AND 100),

    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 10. credit_limits
-- =========================================================

CREATE TABLE IF NOT EXISTS core.credit_limits (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    application_public_id UUID NOT NULL UNIQUE
        REFERENCES core.credit_limit_applications(public_id),

    crop_type_snapshot VARCHAR(30) NOT NULL
        CHECK (crop_type_snapshot IN ('RICE', 'PEPPER', 'SOYBEAN', 'GARLIC', 'ONION')),

    total_limit NUMERIC(15,2) NOT NULL CHECK (total_limit > 0),
    used_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (used_amount >= 0),

    interest_rate NUMERIC(6,4) NOT NULL CHECK (interest_rate >= 0),
    interest_due_day INTEGER NOT NULL CHECK (interest_due_day BETWEEN 1 AND 28),

    principal_due_date DATE NOT NULL,
    expires_at DATE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REPAID', 'EXPIRED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (used_amount <= total_limit)
);

-- =========================================================
-- 11. bss_scores
-- =========================================================

CREATE TABLE IF NOT EXISTS core.bss_scores (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    application_public_id UUID
        REFERENCES core.credit_limit_applications(public_id) ON DELETE SET NULL,

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
-- 12. catalog categories
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.categories (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 13. catalog products
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.products (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    category_public_id UUID NOT NULL
        REFERENCES catalog.categories(public_id),

    name VARCHAR(100) NOT NULL,
    description TEXT,

    price NUMERIC(15,2) NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    unit VARCHAR(20) NOT NULL,

    image_url TEXT,

    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'
        CHECK (status IN ('ON_SALE', 'SOLD_OUT', 'HIDDEN')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 14. catalog cart_items
-- catalog는 users 테이블과 물리 분리될 수 있으므로 user_public_id FK를 강제하지 않는다.
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.cart_items (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL,

    product_public_id UUID NOT NULL
        REFERENCES catalog.products(public_id),

    quantity INTEGER NOT NULL CHECK (quantity > 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cart_items_user_product
        UNIQUE (user_public_id, product_public_id)
);

-- =========================================================
-- 15. catalog bnpl_payment_requests
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.bnpl_payment_requests (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL,

    total_amount NUMERIC(15,2) NOT NULL CHECK (total_amount > 0),

    request_status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
        CHECK (request_status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELLED')),

    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    rejection_reason TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 16. catalog bnpl_payment_request_items
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.bnpl_payment_request_items (
    id BIGSERIAL PRIMARY KEY,

    payment_request_public_id UUID NOT NULL
        REFERENCES catalog.bnpl_payment_requests(public_id),

    product_public_id UUID NOT NULL
        REFERENCES catalog.products(public_id),

    product_name_snapshot VARCHAR(100) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL CHECK (unit_price_snapshot >= 0),

    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_price NUMERIC(15,2) NOT NULL CHECK (total_price >= 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 17. core orders
-- payment_request_public_id는 catalog.bnpl_payment_requests.public_id 값이지만
-- 물리 DB 분리 가능성을 고려하여 FK를 강제하지 않는다.
-- =========================================================

CREATE TABLE IF NOT EXISTS core.orders (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    payment_request_public_id UUID NOT NULL UNIQUE,

    total_amount NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0),

    order_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
        CHECK (order_status IN ('CONFIRMED', 'CANCELLED')),

    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PREPARING'
        CHECK (delivery_status IN ('PREPARING', 'SHIPPING', 'DELIVERED', 'CANCELLED')),

    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    delivery_address_detail VARCHAR(255),
    delivery_zip_code VARCHAR(20) NOT NULL,

    ordered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 18. core order_items
-- product_public_id는 catalog.products.public_id 값이지만
-- 물리 DB 분리 가능성을 고려하여 FK를 강제하지 않는다.
-- =========================================================

CREATE TABLE IF NOT EXISTS core.order_items (
    id BIGSERIAL PRIMARY KEY,

    order_public_id UUID NOT NULL
        REFERENCES core.orders(public_id) ON DELETE CASCADE,

    product_public_id UUID NOT NULL,

    product_name_snapshot VARCHAR(100) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL CHECK (unit_price_snapshot >= 0),

    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_price NUMERIC(15,2) NOT NULL CHECK (total_price >= 0),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 19. credit_usage_ledger
-- payment_request_public_id는 catalog.bnpl_payment_requests.public_id 값이지만
-- 물리 DB 분리 가능성을 고려하여 FK를 강제하지 않는다.
-- =========================================================

CREATE TABLE IF NOT EXISTS core.credit_usage_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES core.credit_limits(public_id),

    payment_request_public_id UUID,
    order_public_id UUID
        REFERENCES core.orders(public_id),

    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),

    usage_type VARCHAR(20) NOT NULL
        CHECK (usage_type IN ('PURCHASE', 'CANCEL', 'ADJUSTMENT')),

    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        (usage_type = 'PURCHASE'
            AND payment_request_public_id IS NOT NULL
            AND order_public_id IS NOT NULL)
        OR usage_type <> 'PURCHASE'
    )
);

-- =========================================================
-- 20. interest_ledger
-- =========================================================

CREATE TABLE IF NOT EXISTS core.interest_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES core.credit_limits(public_id),

    base_principal NUMERIC(15,2) NOT NULL CHECK (base_principal >= 0),
    due_date DATE NOT NULL,

    interest_amount NUMERIC(15,2) NOT NULL CHECK (interest_amount >= 0),
    amount_paid NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),

    paid_at TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'PARTIAL', 'PAID', 'OVERDUE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_interest_ledger_credit_limit_due_date
        UNIQUE (credit_limit_public_id, due_date),

    CHECK (amount_paid <= interest_amount)
);

-- =========================================================
-- 21. principal_repayment_ledger
-- payment_request_public_id는 catalog.bnpl_payment_requests.public_id 값이지만
-- 물리 DB 분리 가능성을 고려하여 FK를 강제하지 않는다.
-- =========================================================

CREATE TABLE IF NOT EXISTS core.principal_repayment_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    credit_limit_public_id UUID NOT NULL
        REFERENCES core.credit_limits(public_id),

    order_public_id UUID NOT NULL UNIQUE
        REFERENCES core.orders(public_id),

    payment_request_public_id UUID,

    due_date DATE NOT NULL,

    principal_amount NUMERIC(15,2) NOT NULL CHECK (principal_amount >= 0),
    amount_paid NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),

    paid_at TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'PARTIAL', 'PAID', 'OVERDUE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (amount_paid <= principal_amount)
);

-- =========================================================
-- 22. loan_overdue_ledger
-- =========================================================

CREATE TABLE IF NOT EXISTS core.loan_overdue_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    credit_limit_public_id UUID NOT NULL
        REFERENCES core.credit_limits(public_id),

    interest_ledger_public_id UUID
        REFERENCES core.interest_ledger(public_id),

    principal_repayment_public_id UUID
        REFERENCES core.principal_repayment_ledger(public_id),

    overdue_type VARCHAR(20) NOT NULL
        CHECK (overdue_type IN ('INTEREST', 'PRINCIPAL')),

    overdue_amount NUMERIC(15,2) NOT NULL CHECK (overdue_amount > 0),
    overdue_days INTEGER NOT NULL CHECK (overdue_days >= 0),

    stage VARCHAR(20) NOT NULL,
    penalty_rate NUMERIC(6,4) NOT NULL CHECK (penalty_rate >= 0),
    penalty_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (penalty_amount >= 0),

    action_taken TEXT,

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
-- 23. notifications
-- =========================================================

CREATE TABLE IF NOT EXISTS core.notifications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_public_id UUID NOT NULL
        REFERENCES core.users(public_id),

    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,

    notification_type VARCHAR(30) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);

-- =========================================================
-- 24. audit_logs
-- =========================================================

CREATE TABLE IF NOT EXISTS core.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    admin_user_public_id UUID NOT NULL
        REFERENCES core.admin_users(public_id),

    user_public_id UUID
        REFERENCES core.users(public_id),

    action VARCHAR(50) NOT NULL,

    target_table VARCHAR(100) NOT NULL,
    target_public_id UUID,

    before_data JSONB,
    after_data JSONB,

    ip_address VARCHAR(50),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 25. crop_repayment_policies
-- =========================================================

CREATE TABLE IF NOT EXISTS core.crop_repayment_policies (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    crop_type VARCHAR(30) NOT NULL UNIQUE
        CHECK (crop_type IN ('RICE', 'PEPPER', 'SOYBEAN', 'GARLIC', 'ONION')),

    crop_name VARCHAR(50) NOT NULL,

    harvest_start_month INTEGER NOT NULL CHECK (harvest_start_month BETWEEN 1 AND 12),
    harvest_end_month INTEGER NOT NULL CHECK (harvest_end_month BETWEEN 1 AND 12),

    sales_start_month INTEGER NOT NULL CHECK (sales_start_month BETWEEN 1 AND 12),
    sales_end_month INTEGER NOT NULL CHECK (sales_end_month BETWEEN 1 AND 12),

    repayment_due_month INTEGER NOT NULL CHECK (repayment_due_month BETWEEN 1 AND 12),
    repayment_due_day INTEGER NOT NULL CHECK (repayment_due_day BETWEEN 1 AND 31),

    description TEXT,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO core.crop_repayment_policies (
    crop_type,
    crop_name,
    harvest_start_month,
    harvest_end_month,
    sales_start_month,
    sales_end_month,
    repayment_due_month,
    repayment_due_day,
    description,
    status
)
VALUES
    ('RICE', '벼', 10, 11, 10, 12, 1, 31, '10월~11월 초 수확, 10월말~12월 자금 회수 기준', 'ACTIVE'),
    ('PEPPER', '고추', 8, 10, 9, 11, 12, 31, '8월~10월 수확, 9월~11월 판매 기준', 'ACTIVE'),
    ('SOYBEAN', '콩', 10, 11, 11, 12, 1, 31, '10월~11월 수확, 11월~12월 판매 기준', 'ACTIVE'),
    ('GARLIC', '마늘', 5, 6, 6, 8, 9, 30, '5월 말~6월 수확, 6월~8월 출하 기준', 'ACTIVE'),
    ('ONION', '양파', 5, 6, 6, 7, 8, 31, '5월 말~6월 수확, 6월~7월 출하 기준', 'ACTIVE')
ON CONFLICT (crop_type) DO UPDATE SET
    crop_name = EXCLUDED.crop_name,
    harvest_start_month = EXCLUDED.harvest_start_month,
    harvest_end_month = EXCLUDED.harvest_end_month,
    sales_start_month = EXCLUDED.sales_start_month,
    sales_end_month = EXCLUDED.sales_end_month,
    repayment_due_month = EXCLUDED.repayment_due_month,
    repayment_due_day = EXCLUDED.repayment_due_day,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- 26. payment_event_process_logs
-- =========================================================

CREATE TABLE IF NOT EXISTS core.payment_event_process_logs (
    id BIGSERIAL PRIMARY KEY,

    event_id UUID NOT NULL UNIQUE,
    payment_request_public_id UUID NOT NULL UNIQUE,

    idempotency_key VARCHAR(120) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PROCESSED', 'FAILED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- Indexes
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_core_users_public_id
    ON core.users(public_id);

CREATE INDEX IF NOT EXISTS idx_core_user_auth_user_public_id
    ON core.user_auth(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_wallets_user_public_id
    ON core.wallets(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_wallet_transactions_wallet_public_id
    ON core.wallet_transactions(wallet_public_id);

CREATE INDEX IF NOT EXISTS idx_core_farmer_profiles_user_public_id
    ON core.farmer_profiles(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_farmer_documents_user_public_id
    ON core.farmer_documents(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_farmer_documents_application_public_id
    ON core.farmer_documents(application_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_limit_applications_user_public_id
    ON core.credit_limit_applications(user_public_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_core_credit_limit_applications_user_in_progress
    ON core.credit_limit_applications(user_public_id)
    WHERE status IN ('REQUESTED', 'PENDING');

CREATE INDEX IF NOT EXISTS idx_core_ass_scores_user_public_id
    ON core.ass_scores(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_ass_scores_application_public_id
    ON core.ass_scores(application_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_limits_user_public_id
    ON core.credit_limits(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_limits_application_public_id
    ON core.credit_limits(application_public_id);

CREATE INDEX IF NOT EXISTS idx_core_bss_scores_user_period
    ON core.bss_scores(user_public_id, period_type, period_year, period_month);

CREATE INDEX IF NOT EXISTS idx_catalog_products_category_public_id
    ON catalog.products(category_public_id);

CREATE INDEX IF NOT EXISTS idx_catalog_cart_items_user_public_id
    ON catalog.cart_items(user_public_id);

CREATE INDEX IF NOT EXISTS idx_catalog_bnpl_payment_requests_user_public_id
    ON catalog.bnpl_payment_requests(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_orders_user_public_id
    ON core.orders(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_orders_payment_request_public_id
    ON core.orders(payment_request_public_id);

CREATE INDEX IF NOT EXISTS idx_core_order_items_order_public_id
    ON core.order_items(order_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_usage_ledger_credit_limit_public_id
    ON core.credit_usage_ledger(credit_limit_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_usage_ledger_order_public_id
    ON core.credit_usage_ledger(order_public_id);

CREATE INDEX IF NOT EXISTS idx_core_credit_usage_ledger_payment_request_public_id
    ON core.credit_usage_ledger(payment_request_public_id);

CREATE INDEX IF NOT EXISTS idx_core_interest_ledger_credit_limit_public_id
    ON core.interest_ledger(credit_limit_public_id);

CREATE INDEX IF NOT EXISTS idx_core_principal_repayment_credit_limit_public_id
    ON core.principal_repayment_ledger(credit_limit_public_id);

CREATE INDEX IF NOT EXISTS idx_core_principal_repayment_order_public_id
    ON core.principal_repayment_ledger(order_public_id);

CREATE INDEX IF NOT EXISTS idx_core_loan_overdue_user_public_id
    ON core.loan_overdue_ledger(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_loan_overdue_credit_limit_public_id
    ON core.loan_overdue_ledger(credit_limit_public_id);

CREATE INDEX IF NOT EXISTS idx_core_notifications_user_public_id
    ON core.notifications(user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_audit_logs_admin_user_public_id
    ON core.audit_logs(admin_user_public_id);

CREATE INDEX IF NOT EXISTS idx_core_admin_users_refresh_token
    ON core.admin_users(refresh_token)
    WHERE refresh_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_core_payment_event_logs_payment_request_public_id
    ON core.payment_event_process_logs(payment_request_public_id);
