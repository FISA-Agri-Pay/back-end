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

ALTER TABLE core.credit_limits
    ADD COLUMN IF NOT EXISTS crop_type_snapshot VARCHAR(30) NOT NULL DEFAULT 'RICE';

ALTER TABLE core.credit_limits
    ADD COLUMN IF NOT EXISTS interest_due_day INTEGER NOT NULL DEFAULT 11;

ALTER TABLE core.credit_limits
    ADD COLUMN IF NOT EXISTS principal_due_date DATE NOT NULL DEFAULT (current_date + interval '330 days');

ALTER TABLE core.credit_limits
    ADD COLUMN IF NOT EXISTS interest_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0350;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_credit_limits_interest_rate'
          AND conrelid = 'core.credit_limits'::regclass
    ) THEN
        ALTER TABLE core.credit_limits
            ADD CONSTRAINT chk_credit_limits_interest_rate
            CHECK (interest_rate >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_credit_limits_crop_type_snapshot'
          AND conrelid = 'core.credit_limits'::regclass
    ) THEN
        ALTER TABLE core.credit_limits
            ADD CONSTRAINT fk_credit_limits_crop_type_snapshot
            FOREIGN KEY (crop_type_snapshot)
            REFERENCES core.crop_repayment_policies(crop_type);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_credit_limits_interest_due_day'
          AND conrelid = 'core.credit_limits'::regclass
    ) THEN
        ALTER TABLE core.credit_limits
            ADD CONSTRAINT chk_credit_limits_interest_due_day
            CHECK (interest_due_day BETWEEN 1 AND 28);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_ledger_credit_limit_due_date
    ON core.interest_ledger(credit_limit_id, due_date);
