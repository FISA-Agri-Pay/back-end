INSERT INTO core.credit_limits (
    user_id,
    application_id,
    total_limit,
    used_amount,
    status
) VALUES
(1, NULL, 1000000, 500000, 'ACTIVE'),
(2, NULL, 1000000, 950000, 'ACTIVE'),
(3, NULL, 1000000, 1000000, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO core.interest_ledger (
    user_id,
    credit_limit_id,
    interest_amount,
    amount_paid,
    status,
    due_date,
    paid_at
)
SELECT
    user_id,
    id,
    interest_amount,
    amount_paid,
    status,
    due_date,
    paid_at
FROM (
    VALUES
        (1, 10000::NUMERIC(15, 2), 10000::NUMERIC(15, 2), 'PAID', current_date - 10, now() - interval '9 days'),
        (2, 10000::NUMERIC(15, 2), 8000::NUMERIC(15, 2), 'PARTIAL', current_date - 10, now() - interval '8 days'),
        (3, 10000::NUMERIC(15, 2), 5000::NUMERIC(15, 2), 'PARTIAL', current_date - 10, now() - interval '7 days')
) AS seed(user_id, interest_amount, amount_paid, status, due_date, paid_at)
JOIN core.credit_limits cl USING (user_id);

INSERT INTO core.principal_repayment_ledger (
    user_id,
    credit_limit_id,
    principal_amount,
    amount_paid,
    status,
    due_date,
    paid_at
)
SELECT
    user_id,
    id,
    principal_amount,
    amount_paid,
    status,
    due_date,
    paid_at
FROM (
    VALUES
        (1, 100000::NUMERIC(15, 2), 100000::NUMERIC(15, 2), 'PAID', current_date - 5, now() - interval '4 days'),
        (2, 100000::NUMERIC(15, 2), 70000::NUMERIC(15, 2), 'PARTIAL', current_date - 5, now() - interval '3 days'),
        (3, 100000::NUMERIC(15, 2), 30000::NUMERIC(15, 2), 'PARTIAL', current_date - 5, now() - interval '2 days')
) AS seed(user_id, principal_amount, amount_paid, status, due_date, paid_at)
JOIN core.credit_limits cl USING (user_id);

INSERT INTO core.loan_overdue_ledger (
    user_id,
    credit_limit_id,
    overdue_amount,
    overdue_days,
    stage,
    resolved_at
)
SELECT
    user_id,
    id,
    overdue_amount,
    overdue_days,
    stage,
    resolved_at
FROM (
    VALUES
        (2, 5000::NUMERIC(15, 2), 3, 'RESOLVED', now() - interval '1 days'),
        (3, 20000::NUMERIC(15, 2), 15, 'IN_PROGRESS', NULL::TIMESTAMP)
) AS seed(user_id, overdue_amount, overdue_days, stage, resolved_at)
JOIN core.credit_limits cl USING (user_id);
