BEGIN;

-- Dummy data for the admin credit review page.
-- This script assumes a local/development database and clears related data first.

TRUNCATE TABLE
    credit_usage_ledger,
    interest_ledger,
    principal_repayment_ledger,
    credit_limits,
    ass_scores,
    farmer_documents,
    credit_limit_applications,
    farmer_profiles,
    users
RESTART IDENTITY CASCADE;

INSERT INTO users (
    id,
    public_id,
    name,
    phone,
    resident_id_hash,
    address,
    address_detail,
    zip_code,
    status,
    created_at,
    updated_at
) VALUES
    (1, '11111111-1111-1111-1111-111111111111', '김농부', '010-1234-5678', 'dummy_resident_hash_001', '경상북도 안동시 농촌마을길 12-3', '101호', '36600', 'ACTIVE', TIMESTAMP '2026-05-20 09:00:00', TIMESTAMP '2026-05-20 09:00:00'),
    (2, '22222222-2222-2222-2222-222222222222', '이수확', '010-2345-6789', 'dummy_resident_hash_002', '전라남도 해남군 들녘로 45', NULL, '59000', 'ACTIVE', TIMESTAMP '2026-05-20 09:10:00', TIMESTAMP '2026-05-20 09:10:00'),
    (3, '33333333-3333-3333-3333-333333333333', '박마늘', '010-3456-7890', 'dummy_resident_hash_003', '충청남도 서산시 밭골길 8', '마늘농장', '31900', 'ACTIVE', TIMESTAMP '2026-05-20 09:20:00', TIMESTAMP '2026-05-20 09:20:00'),
    (4, '44444444-4444-4444-4444-444444444444', '최양파', '010-4567-8901', 'dummy_resident_hash_004', '경상남도 창녕군 양파로 77', NULL, '50300', 'ACTIVE', TIMESTAMP '2026-05-20 09:30:00', TIMESTAMP '2026-05-20 09:30:00'),
    (5, '55555555-5555-5555-5555-555555555555', '정고추', '010-5678-9012', 'dummy_resident_hash_005', '강원특별자치도 영월군 고추밭길 21', NULL, '26200', 'ACTIVE', TIMESTAMP '2026-05-20 09:40:00', TIMESTAMP '2026-05-20 09:40:00'),
    (6, '66666666-6666-6666-6666-666666666666', '한콩심', '010-6789-0123', 'dummy_resident_hash_006', '경기도 파주시 콩마을길 9', '2동', '10800', 'ACTIVE', TIMESTAMP '2026-05-20 09:50:00', TIMESTAMP '2026-05-20 09:50:00');

INSERT INTO farmer_profiles (
    id,
    user_id,
    farm_address,
    farm_address_detail,
    farm_zip_code,
    field_area_m2,
    main_crop,
    has_crop_insurance,
    farming_since,
    created_at,
    updated_at
) VALUES
    (1, 1, '경상북도 안동시 농촌마을길 12-3', '1필지', '36600', 4958.68, 'RICE', true, 2018, TIMESTAMP '2026-05-21 10:00:00', TIMESTAMP '2026-05-21 10:00:00'),
    (2, 2, '전라남도 해남군 들녘로 45', NULL, '59000', 6611.57, 'RICE', true, 2015, TIMESTAMP '2026-05-21 10:10:00', TIMESTAMP '2026-05-21 10:10:00'),
    (3, 3, '충청남도 서산시 밭골길 8', '서산 제2농장', '31900', 3305.79, 'GARLIC', false, 2020, TIMESTAMP '2026-05-21 10:20:00', TIMESTAMP '2026-05-21 10:20:00'),
    (4, 4, '경상남도 창녕군 양파로 77', NULL, '50300', 3966.94, 'ONION', true, 2017, TIMESTAMP '2026-05-21 10:30:00', TIMESTAMP '2026-05-21 10:30:00'),
    (5, 5, '강원특별자치도 영월군 고추밭길 21', NULL, '26200', 2644.63, 'PEPPER', false, 2022, TIMESTAMP '2026-05-21 10:40:00', TIMESTAMP '2026-05-21 10:40:00'),
    (6, 6, '경기도 파주시 콩마을길 9', '콩 재배지', '10800', 1983.47, 'BEAN', true, 2019, TIMESTAMP '2026-05-21 10:50:00', TIMESTAMP '2026-05-21 10:50:00');

INSERT INTO credit_limit_applications (
    id,
    public_id,
    user_id,
    reviewed_by,
    requested_amount,
    approved_amount,
    is_reapplication,
    status,
    rejection_reason,
    applied_at,
    decided_at,
    created_at,
    updated_at
) VALUES
    (1, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 1, NULL, 5000000.00, NULL, false, 'PENDING', NULL, TIMESTAMP '2026-05-26 09:00:00', NULL, TIMESTAMP '2026-05-26 09:00:00', TIMESTAMP '2026-05-26 09:00:00'),
    (2, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 2, NULL, 8000000.00, NULL, false, 'PENDING', NULL, TIMESTAMP '2026-05-26 13:30:00', NULL, TIMESTAMP '2026-05-26 13:30:00', TIMESTAMP '2026-05-26 13:30:00'),
    (3, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 3, NULL, 3500000.00, NULL, true, 'PENDING', NULL, TIMESTAMP '2026-05-27 08:40:00', NULL, TIMESTAMP '2026-05-27 08:40:00', TIMESTAMP '2026-05-27 08:40:00'),
    (4, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 4, NULL, 6500000.00, NULL, false, 'PENDING', NULL, TIMESTAMP '2026-05-27 11:20:00', NULL, TIMESTAMP '2026-05-27 11:20:00', TIMESTAMP '2026-05-27 11:20:00'),
    (5, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 5, NULL, 2000000.00, 1800000.00, false, 'APPROVED', NULL, TIMESTAMP '2026-05-22 09:30:00', TIMESTAMP '2026-05-23 15:00:00', TIMESTAMP '2026-05-22 09:30:00', TIMESTAMP '2026-05-23 15:00:00'),
    (6, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 6, NULL, 2500000.00, NULL, false, 'REJECTED', '서류 미비: 농업경영체 등록확인서의 주소가 신청 정보와 일치하지 않습니다.', TIMESTAMP '2026-05-21 16:10:00', TIMESTAMP '2026-05-22 14:00:00', TIMESTAMP '2026-05-21 16:10:00', TIMESTAMP '2026-05-22 14:00:00');

INSERT INTO farmer_documents (
    id,
    user_id,
    application_id,
    document_type,
    file_url,
    uploaded_at,
    created_at
) VALUES
    (1, 1, 1, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-kim.pdf', TIMESTAMP '2026-05-26 09:05:00', TIMESTAMP '2026-05-26 09:05:00'),
    (2, 1, 1, 'CROP_INSURANCE', '/uploads/credit-documents/dummy/crop-insurance-kim.pdf', TIMESTAMP '2026-05-26 09:06:00', TIMESTAMP '2026-05-26 09:06:00'),
    (3, 2, 2, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-lee.pdf', TIMESTAMP '2026-05-26 13:35:00', TIMESTAMP '2026-05-26 13:35:00'),
    (4, 2, 2, 'CROP_INSURANCE', '/uploads/credit-documents/dummy/crop-insurance-lee.pdf', TIMESTAMP '2026-05-26 13:36:00', TIMESTAMP '2026-05-26 13:36:00'),
    (5, 3, 3, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-park.pdf', TIMESTAMP '2026-05-27 08:45:00', TIMESTAMP '2026-05-27 08:45:00'),
    (6, 4, 4, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-choi.pdf', TIMESTAMP '2026-05-27 11:25:00', TIMESTAMP '2026-05-27 11:25:00'),
    (7, 4, 4, 'CROP_INSURANCE', '/uploads/credit-documents/dummy/crop-insurance-choi.pdf', TIMESTAMP '2026-05-27 11:26:00', TIMESTAMP '2026-05-27 11:26:00'),
    (8, 5, 5, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-jung.pdf', TIMESTAMP '2026-05-22 09:35:00', TIMESTAMP '2026-05-22 09:35:00'),
    (9, 6, 6, 'FARM_MANAGEMENT', '/uploads/credit-documents/dummy/farm-management-han.pdf', TIMESTAMP '2026-05-21 16:15:00', TIMESTAMP '2026-05-21 16:15:00'),
    (10, 6, 6, 'CROP_INSURANCE', '/uploads/credit-documents/dummy/crop-insurance-han.pdf', TIMESTAMP '2026-05-21 16:16:00', TIMESTAMP '2026-05-21 16:16:00');

INSERT INTO ass_scores (
    id,
    user_id,
    application_id,
    estimated_income,
    price_snapshot_date,
    income_score,
    insurance_score,
    farming_career_score,
    total_score,
    calculated_at,
    created_at
) VALUES
    (1, 1, 1, 6370000.00, DATE '2026-05-26', 12, 25, 15, 52, TIMESTAMP '2026-05-26 09:10:00', TIMESTAMP '2026-05-26 09:10:00'),
    (2, 2, 2, 8490000.00, DATE '2026-05-26', 24, 25, 15, 64, TIMESTAMP '2026-05-26 13:40:00', TIMESTAMP '2026-05-26 13:40:00'),
    (3, 3, 3, 2500000.00, DATE '2026-05-27', 12, 0, 11, 23, TIMESTAMP '2026-05-27 08:50:00', TIMESTAMP '2026-05-27 08:50:00'),
    (4, 4, 4, 7570000.00, DATE '2026-05-27', 12, 25, 15, 52, TIMESTAMP '2026-05-27 11:30:00', TIMESTAMP '2026-05-27 11:30:00'),
    (5, 5, 5, 2976000.00, DATE '2026-05-22', 12, 0, 7, 19, TIMESTAMP '2026-05-22 09:40:00', TIMESTAMP '2026-05-22 09:40:00'),
    (6, 6, 6, 1240000.00, DATE '2026-05-21', 12, 25, 11, 48, TIMESTAMP '2026-05-21 16:20:00', TIMESTAMP '2026-05-21 16:20:00');

INSERT INTO credit_limits (
    id,
    public_id,
    user_id,
    application_id,
    total_limit,
    used_amount,
    interest_rate,
    principal_due_date,
    expires_at,
    status,
    created_at,
    updated_at
) VALUES
    (1, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5', 5, 5, 1800000.00, 0.00, 0.0450, DATE '2026-12-31', DATE '2027-05-23', 'ACTIVE', TIMESTAMP '2026-05-23 15:00:00', TIMESTAMP '2026-05-23 15:00:00');

SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1), true);
SELECT setval(pg_get_serial_sequence('farmer_profiles', 'id'), COALESCE((SELECT MAX(id) FROM farmer_profiles), 1), true);
SELECT setval(pg_get_serial_sequence('credit_limit_applications', 'id'), COALESCE((SELECT MAX(id) FROM credit_limit_applications), 1), true);
SELECT setval(pg_get_serial_sequence('farmer_documents', 'id'), COALESCE((SELECT MAX(id) FROM farmer_documents), 1), true);
SELECT setval(pg_get_serial_sequence('ass_scores', 'id'), COALESCE((SELECT MAX(id) FROM ass_scores), 1), true);
SELECT setval(pg_get_serial_sequence('credit_limits', 'id'), COALESCE((SELECT MAX(id) FROM credit_limits), 1), true);

COMMIT;
