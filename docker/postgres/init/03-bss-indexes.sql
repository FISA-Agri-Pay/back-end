CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_monthly
ON core.bss_scores (user_id, period_type, period_year, period_month)
WHERE application_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_annual
ON core.bss_scores (user_id, period_type, period_year)
WHERE application_id IS NULL
  AND period_type = 'ANNUAL'
  AND period_month IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_with_app
ON core.bss_scores (user_id, application_id, period_type, period_year, period_month)
WHERE application_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_with_app_annual
ON core.bss_scores (user_id, application_id, period_type, period_year)
WHERE application_id IS NOT NULL
  AND period_type = 'ANNUAL'
  AND period_month IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_ledger_credit_limit_due_date
ON core.interest_ledger (credit_limit_id, due_date);

CREATE INDEX IF NOT EXISTS idx_wallets_user_id
ON core.wallets (user_id);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_id
ON core.wallet_transactions (wallet_id);

CREATE INDEX IF NOT EXISTS idx_loan_overdue_ledger_interest_ledger_id
ON core.loan_overdue_ledger (interest_ledger_id);
