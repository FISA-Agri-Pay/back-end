CREATE TABLE IF NOT EXISTS core.wallets (
                                            id BIGSERIAL PRIMARY KEY,
                                            user_id BIGINT NOT NULL UNIQUE,
                                            balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    deposit_bank_name VARCHAR(50) NOT NULL DEFAULT 'local-bank',
    deposit_account_number VARCHAR(50) NOT NULL DEFAULT '0000000000',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    CONSTRAINT chk_wallets_balance
    CHECK (balance >= 0),
    CONSTRAINT chk_wallets_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
    );

CREATE TABLE IF NOT EXISTS core.wallet_transactions (
                                                        id BIGSERIAL PRIMARY KEY,
                                                        wallet_id BIGINT NOT NULL,
                                                        transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    balance_after NUMERIC(15, 2) NOT NULL,
    related_type VARCHAR(50),
    related_id BIGINT,
    description VARCHAR(500),
    transacted_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP DEFAULT now(),
    CONSTRAINT fk_wallet_transactions_wallet_id
    FOREIGN KEY (wallet_id) REFERENCES core.wallets(id),
    CONSTRAINT chk_wallet_transactions_type
    CHECK (transaction_type IN ('DEPOSIT', 'INTEREST_PAYMENT', 'PRINCIPAL_PAYMENT', 'REFUND', 'ADJUSTMENT')),
    CONSTRAINT chk_wallet_transactions_amount
    CHECK (amount > 0),
    CONSTRAINT chk_wallet_transactions_balance_after
    CHECK (balance_after >= 0)
    );

ALTER TABLE core.loan_overdue_ledger
    ADD COLUMN IF NOT EXISTS interest_ledger_id BIGINT;

ALTER TABLE core.loan_overdue_ledger
    ADD COLUMN IF NOT EXISTS resolved_amount NUMERIC(15, 2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_wallet_transactions_wallet_id'
          AND conrelid = 'core.wallet_transactions'::regclass
    ) THEN
        ALTER TABLE core.wallet_transactions
            ADD CONSTRAINT fk_wallet_transactions_wallet_id
            FOREIGN KEY (wallet_id)
            REFERENCES core.wallets(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_id
    ON core.wallet_transactions(wallet_id);

CREATE INDEX IF NOT EXISTS idx_loan_overdue_ledger_interest_ledger_id
    ON core.loan_overdue_ledger(interest_ledger_id);
