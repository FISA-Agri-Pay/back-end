CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_monthly
ON core.bss_scores (user_id, period_type, period_year, period_month)
WHERE application_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bss_scores_with_app
ON core.bss_scores (user_id, application_id, period_type, period_year, period_month)
WHERE application_id IS NOT NULL;