CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE IF NOT EXISTS core.users
(
    id               BIGSERIAL    PRIMARY KEY,
    public_id        UUID         NOT NULL UNIQUE,
    name             VARCHAR(50)  NOT NULL,
    phone            VARCHAR(20)  NOT NULL UNIQUE,
    resident_id_hash VARCHAR(67)  UNIQUE,
    resident_id_enc  VARCHAR(100),
    address          VARCHAR(255) NOT NULL,
    address_detail   VARCHAR(255),
    zip_code         VARCHAR(10)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS core.user_auth
(
    id             BIGSERIAL    PRIMARY KEY,
    user_id        UUID         NOT NULL UNIQUE REFERENCES core.users (public_id),
    password_hash  VARCHAR(255) NOT NULL,
    pin_hash       VARCHAR(255),
    refresh_token  VARCHAR(512),
    pin_changed_at TIMESTAMP,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS core.admin_users
(
    id             BIGSERIAL    PRIMARY KEY,
    public_id      UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    email          VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(50)  NOT NULL,

    role           VARCHAR(20)  NOT NULL DEFAULT 'ADMIN'
                       CHECK (role IN ('ADMIN')),

    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    refresh_token  VARCHAR(64),
    last_login_at  TIMESTAMP,

    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Existing DB migration only. New installations already include this column in CREATE TABLE above.
ALTER TABLE IF EXISTS core.admin_users
    ADD COLUMN IF NOT EXISTS refresh_token VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_users_phone ON core.users (phone);
CREATE INDEX IF NOT EXISTS idx_user_auth_refresh_token ON core.user_auth (refresh_token)
    WHERE refresh_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_admin_users_refresh_token ON core.admin_users (refresh_token)
    WHERE refresh_token IS NOT NULL;
