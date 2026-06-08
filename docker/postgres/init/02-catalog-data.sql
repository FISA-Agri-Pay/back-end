-- =========================================================
-- Seed Data: catalog categories & products
-- =========================================================

INSERT INTO catalog.categories (name, status) VALUES
    ('씨앗/모종', 'ACTIVE'),
    ('비료/자재', 'ACTIVE'),
    ('영농 서비스', 'ACTIVE')
ON CONFLICT (name) DO NOTHING;

-- 씨앗/모종
INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '고추 모종 (청양) 50주', '정식용 플러그 모종, 4월 중순~5월 초 정식 적기', 15000, 500, '주', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '씨앗/모종';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '마늘 종구 1kg (남도)', '국내산 남도마늘 종구, 9월~10월 파종용', 8000, 1000, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '씨앗/모종';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '콩 종자 (태광) 1kg', '다수확 콩 품종, 6월 파종용', 9000, 400, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '씨앗/모종';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '양파 모종 (창녕) 200주', '창녕 재래종 양파 모종, 10월~11월 정식용', 12000, 600, '주', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '씨앗/모종';

-- 비료/자재
INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '요소비료 20kg', '질소 함량 46%, 벼·고추·콩 등 전작물에 적합한 기본 질소 비료', 25000, 200, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '복합비료(21-17-17) 20kg', 'N-P-K 균형 배합, 생육 초기 밑거름용', 32000, 150, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '유기질비료 20kg', '축분 발효 원료, 토양 개량 및 지력 증진', 18000, 300, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '제초제 (그라목손) 500ml', '비선택성 경엽처리제, 논두렁·밭두렁 잡초 방제', 15000, 100, 'ml', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '살충제 (디메토유제) 1L', '진딧물·응애류 방제, 고추·마늘·양파 적용', 22000, 80, 'L', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '살균제 (만코지수화제) 1kg', '역병·탄저병 예방, 고추·콩 적용', 19000, 120, 'kg', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '멀칭 비닐 (흑색) 100m', '두께 0.02mm, 잡초 억제 및 지온 유지용', 28000, 150, 'm', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '점적 호스 100m', '저압 점적 관수용, 고추·마늘 밭 관개에 적합', 45000, 100, 'm', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '비료/자재';

-- 영농 서비스
INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '드론 방제 서비스 (1,000㎡)', '농업용 드론을 이용한 농약 살포 서비스, 1,000㎡ 기준', 50000, 99, '건', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '영농 서비스';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '토양 성분 분석 서비스', 'pH·유기물·질소·인산·칼리 등 12개 항목 분석 후 시비 처방서 제공', 30000, 99, '건', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '영농 서비스';

INSERT INTO catalog.products (category_public_id, name, description, price, stock_quantity, unit, status)
SELECT c.public_id, '농작물 재해보험 컨설팅', '품목별 재해보험 가입 절차 및 보장 내용 1:1 상담', 0, 99, '건', 'ON_SALE'
FROM catalog.categories c WHERE c.name = '영농 서비스';
