ALTER TABLE core.loan_overdue_ledger
    ADD COLUMN IF NOT EXISTS principal_repayment_ledger_id BIGINT;

DO $$
BEGIN
    IF to_regclass('core.principal_repayment_ledger') IS NOT NULL
       AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_loan_overdue_principal_repayment_ledger_id'
          AND conrelid = 'core.loan_overdue_ledger'::regclass
    ) THEN
        ALTER TABLE core.loan_overdue_ledger
            ADD CONSTRAINT fk_loan_overdue_principal_repayment_ledger_id
            FOREIGN KEY (principal_repayment_ledger_id)
            REFERENCES core.principal_repayment_ledger(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_loan_overdue_ledger_single_source'
          AND conrelid = 'core.loan_overdue_ledger'::regclass
    ) THEN
        ALTER TABLE core.loan_overdue_ledger
            ADD CONSTRAINT chk_loan_overdue_ledger_single_source
            CHECK (num_nonnulls(interest_ledger_id, principal_repayment_ledger_id) = 1)
            NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_loan_overdue_ledger_principal_repayment_ledger_id
    ON core.loan_overdue_ledger(principal_repayment_ledger_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_loan_overdue_active_principal_repayment_ledger_id
    ON core.loan_overdue_ledger(principal_repayment_ledger_id)
    WHERE principal_repayment_ledger_id IS NOT NULL
      AND resolved_at IS NULL;
