CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE IF NOT EXISTS core.users
(
    id               BIGSERIAL    PRIMARY KEY,
    public_id        UUID         NOT NULL UNIQUE,
    name             VARCHAR(50)  NOT NULL,
    phone            VARCHAR(20)  NOT NULL UNIQUE,
    resident_id_hash VARCHAR(64),
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
    role           VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON core.users (phone);
CREATE INDEX IF NOT EXISTS idx_user_auth_refresh_token ON core.user_auth (refresh_token)
    WHERE refresh_token IS NOT NULL;