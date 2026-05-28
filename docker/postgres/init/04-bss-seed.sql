INSERT INTO core.credit_limits (
    user_id,
    application_id,
    total_limit,
    used_amount,
    status
)
SELECT
    user_id,
    NULL,
    total_limit,
    used_amount,
    status
FROM (
    VALUES
        (1, 1000000::NUMERIC(15, 2), 500000::NUMERIC(15, 2), 'ACTIVE'),
        (2, 1000000::NUMERIC(15, 2), 950000::NUMERIC(15, 2), 'ACTIVE'),
        (3, 1000000::NUMERIC(15, 2), 1000000::NUMERIC(15, 2), 'ACTIVE')
) AS seed(user_id, total_limit, used_amount, status)
WHERE NOT EXISTS (
    SELECT 1
    FROM core.credit_limits cl
    WHERE cl.user_id = seed.user_id
);

INSERT INTO core.wallets (
    user_id,
    balance,
    deposit_account_number,
    status
)
SELECT
    user_id,
    balance,
    deposit_account_number,
    'ACTIVE'
FROM (
    VALUES
        (1, 30000::NUMERIC(15, 2), 'local-0001'),
        (2, 5000::NUMERIC(15, 2), 'local-0002'),
        (3, 0::NUMERIC(15, 2), 'local-0003')
) AS seed(user_id, balance, deposit_account_number)
WHERE NOT EXISTS (
    SELECT 1
    FROM core.wallets w
    WHERE w.user_id = seed.user_id
);

INSERT INTO core.interest_ledger (
    credit_limit_id,
    base_principal,
    due_date,
    interest_amount,
    amount_paid,
    status,
    paid_at
)
SELECT
    id,
    base_principal,
    due_date,
    interest_amount,
    amount_paid,
    status,
    paid_at
FROM (
    VALUES
        (1, 500000::NUMERIC(15, 2), 10000::NUMERIC(15, 2), 10000::NUMERIC(15, 2), 'PAID', current_date - 10, now() - interval '9 days'),
        (2, 950000::NUMERIC(15, 2), 10000::NUMERIC(15, 2), 8000::NUMERIC(15, 2), 'PARTIAL', current_date - 10, now() - interval '8 days'),
        (3, 1000000::NUMERIC(15, 2), 10000::NUMERIC(15, 2), 5000::NUMERIC(15, 2), 'PARTIAL', current_date - 10, now() - interval '7 days')
) AS seed(user_id, base_principal, interest_amount, amount_paid, status, due_date, paid_at)
JOIN core.credit_limits cl USING (user_id)
WHERE NOT EXISTS (
    SELECT 1
    FROM core.interest_ledger il
    WHERE il.credit_limit_id = cl.id
      AND il.due_date = seed.due_date
      AND il.interest_amount = seed.interest_amount
);

INSERT INTO core.principal_repayment_ledger (
    credit_limit_id,
    due_date,
    principal_amount,
    amount_paid,
    status,
    paid_at
)
SELECT
    id,
    due_date,
    principal_amount,
    amount_paid,
    status,
    paid_at
FROM (
    VALUES
        (1, 100000::NUMERIC(15, 2), 100000::NUMERIC(15, 2), 'PAID', current_date - 5, now() - interval '4 days'),
        (2, 100000::NUMERIC(15, 2), 70000::NUMERIC(15, 2), 'PARTIAL', current_date - 5, now() - interval '3 days'),
        (3, 100000::NUMERIC(15, 2), 30000::NUMERIC(15, 2), 'PARTIAL', current_date - 5, now() - interval '2 days')
) AS seed(user_id, principal_amount, amount_paid, status, due_date, paid_at)
JOIN core.credit_limits cl USING (user_id)
WHERE NOT EXISTS (
    SELECT 1
    FROM core.principal_repayment_ledger pr
    WHERE pr.credit_limit_id = cl.id
      AND pr.due_date = seed.due_date
      AND pr.principal_amount = seed.principal_amount
);

INSERT INTO core.loan_overdue_ledger (
    user_id,
    credit_limit_id,
    principal_repayment_ledger_id,
    overdue_amount,
    overdue_days,
    stage,
    resolved_at
)
SELECT
    user_id,
    cl.id,
    pr.id,
    overdue_amount,
    overdue_days,
    stage,
    resolved_at
FROM (
    VALUES
        (2, 5000::NUMERIC(15, 2), 3, 'RESOLVED', now() - interval '1 days'),
        (3, 20000::NUMERIC(15, 2), 15, 'IN_PROGRESS', NULL::TIMESTAMP)
) AS seed(user_id, overdue_amount, overdue_days, stage, resolved_at)
JOIN core.credit_limits cl USING (user_id)
JOIN core.principal_repayment_ledger pr
  ON pr.credit_limit_id = cl.id
 AND pr.due_date = current_date - 5
WHERE NOT EXISTS (
    SELECT 1
    FROM core.loan_overdue_ledger lo
    WHERE lo.credit_limit_id = cl.id
      AND lo.overdue_amount = seed.overdue_amount
      AND lo.overdue_days = seed.overdue_days
      AND lo.stage = seed.stage
);
