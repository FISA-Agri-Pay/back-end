TRUNCATE TABLE categories CASCADE;
TRUNCATE TABLE products CASCADE;

-- ==============================================================================
-- 1. categories (카테고리) 더미 데이터 삽입
-- 설명: id, status, created_at, updated_at은 기본값(Default) 설정이 있으므로 
--       필수 입력값인 'name'만 지정하여 데이터를 삽입합니다.
-- ==============================================================================
INSERT INTO categories (name) 
VALUES 
    ('영농 서비스'),
    ('비료/자재'),
    ('씨앗/모종');


-- ==============================================================================
-- 2. products (상품) 더미 데이터 삽입
-- 설명: 
--  - public_id(UUID), created_at, updated_at은 자동 생성되므로 생략했습니다.
--  - category_id는 하드코딩된 숫자 대신, 서브쿼리(SELECT)를 통해 정확하게 매핑합니다.
--  - price와 stock_quantity는 CHECK 제약조건에 맞춰 0 이상의 값으로 입력했습니다.
-- ==============================================================================
INSERT INTO products (category_id, name, description, price, stock_quantity, unit, status) 
VALUES 
    -- [1] 영농 서비스 카테고리 상품
    (
        (SELECT id FROM categories WHERE name = '영농 서비스'), 
        '드론 방제 서비스(1,000평)', 
        '정밀 농업용 드론을 활용한 빠르고 균일한 농약 및 영양제 살포 대행 서비스입니다.', 
        150000, 999, '회', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '영농 서비스'), 
        '트랙터 로터리 작업(1일)', 
        '대형 트랙터를 이용한 밭갈이 및 흙 부수기(평탄화) 전문 작업 서비스입니다.', 
        300000, 10, '일', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '영농 서비스'), 
        '이앙기 모내기 대행(1,000평)', 
        '승용 이앙기를 이용한 신속하고 정확한 모내기 대행 작업입니다.', 
        120000, 50, '회', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '영농 서비스'), 
        '콤바인 벼 수확 대행(1,000평)', 
        '가을철 벼 베기 및 탈곡을 한 번에 처리해 드리는 수확 대행 서비스입니다.', 
        200000, 30, '회', 'ON_SALE'
    ),

    -- [2] 비료/자재 카테고리 상품
    (
        (SELECT id FROM categories WHERE name = '비료/자재'), 
        '맞춤형 복합 비료 20kg(1포)', 
        '작물 생육에 필수적인 질소, 인산, 칼륨이 최적의 비율로 배합된 밑거름용 비료입니다.', 
        15000, 500, '포', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '비료/자재'), 
        '친환경 유기농 퇴비 10kg', 
        '화학 성분이 전혀 들어가지 않은 100% 천연 발효 유기농 가축분 퇴비입니다.', 
        8000, 1000, '포', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '비료/자재'), 
        '질소 요소비료 20kg(1포)', 
        '작물의 잎과 줄기 생장을 촉진하는 고농도 질소질 웃거름 비료입니다.', 
        12000, 300, '포', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '비료/자재'), 
        '다목적 원예용 상토 50L', 
        '보수력과 통기성이 우수하여 모종 기르기에 적합한 프리미엄 배양토입니다.', 
        9500, 250, '포', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '비료/자재'), 
        '농업용 흑색 멀칭 비닐(1000m)', 
        '잡초 방제 및 토양 수분 유지에 탁월한 고품질 농업용 멀칭 필름입니다.', 
        35000, 100, '롤', 'ON_SALE'
    ),

    -- [3] 씨앗/모종 카테고리 상품
    (
        (SELECT id FROM categories WHERE name = '씨앗/모종'), 
        '청양고추 모종 100구', 
        '병충해에 강하고 매운맛이 일품인 우량 청양고추 모종 1판(100구)입니다.', 
        15000, 200, '판', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '씨앗/모종'), 
        '항암 가을 배추 모종 105구', 
        '속이 꽉 차고 아삭한 식감을 자랑하는 김장용 프리미엄 배추 모종입니다.', 
        12000, 150, '판', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '씨앗/모종'), 
        '해남 꿀 고구마 순(100본 1단)', 
        '활착률이 매우 우수하고 당도가 높은 정품 꿀 고구마 종순입니다.', 
        8000, 300, '단', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '씨앗/모종'), 
        '미백 찰옥수수 종자(2,000립 1봉)', 
        '발아율이 뛰어나고 찰기가 강해 맛이 좋은 찰옥수수 코팅 종자입니다.', 
        20000, 50, '봉', 'ON_SALE'
    ),
    (
        (SELECT id FROM categories WHERE name = '씨앗/모종'), 
        '조선 외대파 모종 200구', 
        '추위에 강하고 뿌리 활착이 잘 되어 초보자도 키우기 쉬운 대파 모종입니다.', 
        18000, 100, '판', 'ON_SALE'
    );