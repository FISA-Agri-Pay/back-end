-- =========================================================
-- KongKongFarm service-catalog dummy data
--
-- 실행 전제:
-- 1. service-catalog/catalog-schema.sql을 먼저 적용한다.
-- 2. 이 파일은 schema/table을 다시 만들지 않고 더미 데이터만 삽입한다.
-- 3. 반복 실행해도 같은 public_id 기준으로 갱신되도록 작성한다.
-- =========================================================

-- =========================================================
-- catalog categories
-- =========================================================

INSERT INTO catalog.categories (
    public_id,
    name,
    status,
    created_at,
    updated_at
)
VALUES
    ('11111111-1111-1111-1111-111111111111', '쌀/잡곡', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', '채소', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333333', '과일', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (public_id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- catalog products
-- =========================================================

INSERT INTO catalog.products (
    public_id,
    category_public_id,
    name,
    description,
    price,
    stock_quantity,
    unit,
    image_url,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        '11111111-1111-1111-1111-111111111111',
        '김포 고시히카리 쌀 10kg',
        '당일 도정한 김포산 고시히카리 쌀입니다.',
        34900.00,
        120,
        '10kg',
        'https://example.com/images/rice-10kg.jpg',
        'ON_SALE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '22222222-2222-2222-2222-222222222222',
        '친환경 상추 500g',
        '쌈 채소로 좋은 친환경 상추입니다.',
        5900.00,
        80,
        '500g',
        'https://example.com/images/lettuce-500g.jpg',
        'ON_SALE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        '33333333-3333-3333-3333-333333333333',
        '제철 사과 3kg',
        '아삭하고 당도 높은 제철 사과입니다.',
        21900.00,
        50,
        '3kg',
        'https://example.com/images/apple-3kg.jpg',
        'ON_SALE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (public_id) DO UPDATE SET
    category_public_id = EXCLUDED.category_public_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    unit = EXCLUDED.unit,
    image_url = EXCLUDED.image_url,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- catalog cart_items
-- 테스트용 user_public_id: 99999999-9999-9999-9999-999999999999
-- =========================================================

INSERT INTO catalog.cart_items (
    public_id,
    user_public_id,
    product_public_id,
    quantity,
    created_at,
    updated_at
)
VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        1,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        2,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (user_public_id, product_public_id) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- catalog bnpl_payment_requests
-- checkout 조회 API 테스트용 결제 요청 데이터이다.
-- =========================================================

INSERT INTO catalog.bnpl_payment_requests (
    public_id,
    user_public_id,
    total_amount,
    request_status,
    requested_at,
    processed_at,
    rejection_reason,
    created_at,
    updated_at
)
VALUES (
    'cccccccc-cccc-cccc-cccc-ccccccccccc1',
    '99999999-9999-9999-9999-999999999999',
    46700.00,
    'REQUESTED',
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (public_id) DO UPDATE SET
    user_public_id = EXCLUDED.user_public_id,
    total_amount = EXCLUDED.total_amount,
    request_status = EXCLUDED.request_status,
    requested_at = EXCLUDED.requested_at,
    processed_at = EXCLUDED.processed_at,
    rejection_reason = EXCLUDED.rejection_reason,
    updated_at = CURRENT_TIMESTAMP;

-- =========================================================
-- catalog bnpl_payment_request_items
-- 이 테이블은 public_id나 unique key가 없으므로 동일 snapshot 존재 여부로 중복 삽입을 방지한다.
-- =========================================================

INSERT INTO catalog.bnpl_payment_request_items (
    payment_request_public_id,
    product_public_id,
    product_name_snapshot,
    unit_price_snapshot,
    quantity,
    total_price,
    created_at
)
SELECT
    'cccccccc-cccc-cccc-cccc-ccccccccccc1',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    '김포 고시히카리 쌀 10kg',
    34900.00,
    1,
    34900.00,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM catalog.bnpl_payment_request_items
    WHERE payment_request_public_id = 'cccccccc-cccc-cccc-cccc-ccccccccccc1'
      AND product_public_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'
);

INSERT INTO catalog.bnpl_payment_request_items (
    payment_request_public_id,
    product_public_id,
    product_name_snapshot,
    unit_price_snapshot,
    quantity,
    total_price,
    created_at
)
SELECT
    'cccccccc-cccc-cccc-cccc-ccccccccccc1',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    '친환경 상추 500g',
    5900.00,
    2,
    11800.00,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM catalog.bnpl_payment_request_items
    WHERE payment_request_public_id = 'cccccccc-cccc-cccc-cccc-ccccccccccc1'
      AND product_public_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'
);
