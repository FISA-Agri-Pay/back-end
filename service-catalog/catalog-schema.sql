-- =========================================================
-- KongKongFarm PostgreSQL Init Schema
-- Version: service-catalog schema only
--
-- 기준:
-- 1. local/dev 환경은 동일 PostgreSQL 인스턴스를 사용한다.
-- 2. service-catalog 소유 테이블은 catalog schema에 둔다.
-- 3. 내부 PK는 BIGSERIAL id를 사용한다.
-- 4. 외부 API, Kafka, Redis, 서비스 간 참조는 public_id(UUID)를 사용한다.
-- 5. 서비스 경계를 넘는 참조는 DB FK를 강제하지 않고 UUID 값과 애플리케이션 로직으로 관리한다.
-- =========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS catalog;

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
-- Indexes
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_catalog_products_category_public_id
    ON catalog.products(category_public_id);

CREATE INDEX IF NOT EXISTS idx_catalog_cart_items_user_public_id
    ON catalog.cart_items(user_public_id);

CREATE INDEX IF NOT EXISTS idx_catalog_bnpl_payment_requests_user_public_id
    ON catalog.bnpl_payment_requests(user_public_id);
