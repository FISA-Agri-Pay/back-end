# 백엔드 아키텍처 설계

> 프로젝트: Kong Kong Farm (농업인 한도 기반 외상 구매 서비스)
> 스택: Spring Boot 3.3.4 · Java 21 · Gradle Groovy DSL · PostgreSQL · Kafka · Redis
> 팀: Dev6 · 5명 · 2개월

---

## 1. 프로젝트 구조

### 1.1 모듈 구성

과도한 레포 분리형 MSA는 지양하고, 모노레포 Gradle 멀티모듈 안에서 실제 배포 단위 기준으로 4개 실행 서비스를 구성한다.

```
kkpp-backend/
├── common-core        # BaseEntity, 공통 예외, 응답 포맷, enum, event
├── common-security    # JWT 검증 필터, @AuthUser
│
├── service-catalog    # AWS 배포 — 홈, 상품 조회, 카테고리, 장바구니, BNPL 결제요청 접수
├── service-core       # On-Prem 배포 — 회원, 인증, 한도 신청, 외상 승인, 주문 확정, 지갑, 원장
├── service-batch      # On-Prem 배포 — BSS 월별/연도별 산출, 이자 청구, 연체 감지
└── service-admin      # On-Prem 배포 — 관리자 인증, 한도 심사, 상품/주문/연체 관리
```

각 `service-*`는 독립 실행 모듈이며, 별도 `bootJar`, Docker 이미지, K8s 리소스를 가질 수 있다.

| 서비스 | 배치 위치 | K8s 리소스 | 역할 |
|--------|----------|-----------|------|
| `service-catalog` | AWS EKS | Deployment | 홈, 상품 조회, 카테고리, 장바구니, BNPL 결제요청 접수 |
| `service-core` | On-Prem K8s | Deployment | 회원, 인증, 한도 신청, 외상 승인, 주문 확정, 지갑, 원장 |
| `service-batch` | On-Prem K8s | CronJob | BSS 산출, 이자 청구, 연체 감지 |
| `service-admin` | On-Prem K8s | Deployment | 관리자 인증, 한도 심사, 상품/주문/연체 관리 |

기존 도메인별 서비스(`service-auth`, `service-credit`, `service-order`, `service-ledger`)는 독립 MSA로 분리하지 않고 `service-core` 내부 도메인 패키지로 관리한다.

### 1.2 패키지 구조

각 서비스는 도메인 중심 패키지 구조를 따른다.

```
com.kkpp.{서비스명}/
├── {도메인명}/
│   ├── controller/    # @RestController — HTTP 요청/응답만 처리
│   ├── service/       # 비즈니스 로직
│   ├── repository/    # JpaRepository 상속 인터페이스
│   ├── domain/        # @Entity 클래스
│   ├── dto/
│   │   ├── request/   # 요청 DTO
│   │   └── response/  # 응답 DTO
│   └── mapper/        # MapStruct 인터페이스
└── global/
    ├── config/        # SecurityConfig, RedisConfig, KafkaConfig 등
    └── exception/     # 서비스별 예외 (공통 예외는 common-core)
```

#### service-catalog 패키지 예시

```
com.kkpp.catalog/
├── product/
├── category/
├── cart/
├── bnplrequest/   # BNPL 결제요청 접수 및 이벤트 발행
├── home/
└── global/
```

#### service-core 패키지 예시

```
com.kkpp.core/
├── auth/
├── user/
├── credit/
├── bnpl/
├── wallet/
├── order/
├── ledger/
└── global/
```

#### service-batch 패키지 예시

```
com.kkpp.batch/
├── bss/         # BSS 월별/연도별 점수 산출
├── interest/    # 월 이자 청구 레코드 생성
├── overdue/     # 연체 감지 및 단계 갱신
└── global/
```
`interest` 배치는 `ACTIVE` 상태의 `credit_limits` 중 `used_amount > 0`인 데이터를 대상으로 월별 `interest_ledger`를 생성한다.  
동일 한도와 동일 납부 예정일에 중복 생성되지 않도록 `credit_limit_public_id + due_date` 기준으로 중복 생성을 방지한다.

#### service-admin 패키지 예시

```
com.kkpp.admin/
├── adminauth/
├── dashboard/
├── creditreview/
├── overduemanagement/
├── productmanagement/
├── ordermanagement/
├── audit/
└── global/
```

---

## 2. 서비스 경계 규칙

### 2.1 서비스 경계

| 서비스 | 배치 위치 | 역할 |
|--------|----------|------|
| `service-catalog` | AWS EKS | 홈, 상품 조회, 카테고리, 장바구니, BNPL 결제요청 접수 |
| `service-core` | On-Prem K8s | 회원, 인증, 한도 신청, 외상 승인, 주문 확정, 지갑, 원장 |
| `service-batch` | On-Prem K8s | BSS 산출, 이자 청구, 연체 감지 |
| `service-admin` | On-Prem K8s | 관리자 인증, 한도 심사, 상품/주문/연체 관리 |

### 2.2 목표 DB 분리와 local/dev 단일 DB 전략

본 프로젝트의 목표 아키텍처는 AWS 채널계와 On-Prem 금융 업무계의 DB를 분리하는 구조이다.

목표 배포 기준:

| DB | 담당 서비스 | 포함 테이블 |
|----|------------|------------|
| AWS Catalog DB | service-catalog | categories, products, cart_items, bnpl_payment_requests, bnpl_payment_request_items |
| On-Prem Core DB | service-core, service-admin, service-batch | users, user_auth, farmer_profiles, farmer_documents, credit_limit_applications, ass_scores, bss_scores, credit_limits, orders, order_items, credit_usage_ledger, interest_ledger, principal_repayment_ledger, loan_overdue_ledger, wallets, wallet_transactions, notifications, admin_users, audit_logs |

단, 내부 개발과 local/dev 테스트 단계에서는 개발 편의와 초기 구현 속도를 위해 **단일 PostgreSQL 인스턴스의 public schema에 모든 테이블을 생성**한다.

| 환경 | DB 구성 | 설명 |
|------|--------|------|
| local | 단일 PostgreSQL + public schema | 로컬 개발 편의 목적 |
| dev | 단일 PostgreSQL + public schema | 팀 통합 테스트 편의 목적 |
| prod/target | AWS Catalog DB + On-Prem Core DB | 목표 하이브리드 구조 |

local/dev 단일 DB 구성은 물리 DB 분리 전 단계의 테스트 편의 구조이며, 서비스 간 책임 경계를 없앤다는 의미가 아니다. 서비스별 테이블 소유권은 문서로 구분하고, 코드 레벨에서는 다른 서비스의 Entity나 Repository를 직접 참조하지 않는다.

향후 최종 배포 또는 검증 단계에서는 `public_id(UUID)`를 서비스 간 논리 참조 키로 사용하여 AWS Catalog DB와 On-Prem Core DB를 분리할 수 있도록 한다.

### 2.3 서비스 소유 테이블 직접 접근 금지

local/dev에서는 모든 테이블이 같은 public schema에 존재할 수 있다. 하지만 이는 테스트 편의 목적이며, 다른 서비스가 소유한 테이블을 직접 조회하거나 수정해도 된다는 의미가 아니다.

금지 사항:

- 다른 서비스의 Entity 직접 import
- 다른 서비스의 Repository 직접 주입
- 다른 서비스 소유 테이블에 대한 직접 `SELECT`, `INSERT`, `UPDATE`, `DELETE`
- 서비스 경계를 우회하는 직접 JOIN

```java
// ❌ 금지 — service-core에서 catalog의 Repository 직접 사용
private final ProductRepository productRepository;

// ❌ 금지 — service-core에서 catalog 테이블 직접 조인
@Query(value = """
    SELECT p.name, p.price
    FROM products p
    WHERE p.public_id = :productPublicId
""", nativeQuery = true)
ProductInfo findProductDirectly(UUID productPublicId);

// ✅ 허용 — service-catalog API 호출 또는 이벤트 payload의 스냅샷 사용
CatalogProductResponse product = catalogClient.getProduct(productPublicId);
```

### 2.4 서비스 간 통신 기준

같은 서비스 내부 도메인 간 처리는 메서드 호출로 처리한다. 다른 실행 서비스 간 연동은 HTTP API 또는 Kafka 이벤트로 처리한다.

| 상황 | 방식 | 예시 |
|------|------|------|
| 즉시 결과가 필요한 조회 | HTTP | 주문 확정 시 상품 가격/재고 확인 |
| 사용자 요청 접수 후 금융 후속 처리 | Kafka | BNPL 결제요청 생성 후 core 처리 요청 |
| 배치 완료 후속 처리 | Kafka | BSS 산출 완료 후 알림 발송 |
| 상태 변경 알림 | Kafka | 한도 승인/반려, BNPL 승인/반려 알림 |

Kafka는 API 호출 대체 수단이 아니라 비동기 도메인 이벤트 전달 수단으로 사용한다.

### 2.5 상품, 결제요청, 주문의 경계

상품과 장바구니 데이터의 소유권은 `service-catalog`에 둔다. 결제요청 접수도 AWS 채널계인 `service-catalog`가 담당한다.

```
상품 조회              → service-catalog
장바구니               → service-catalog, cart_items 원본 + AWS ElastiCache 캐시
BNPL 결제요청 접수     → service-catalog, bnpl_payment_requests REQUESTED 생성
BNPL 금융 검증         → service-core
주문 확정              → service-core
한도 차감/원장 기록     → service-core
상품 등록/수정          → service-admin → service-catalog 내부 API 호출 → products 저장
```

`service-admin`은 관리자 인증과 권한 검증 후 상품 관리 요청을 `service-catalog`로 전달한다. `service-admin`은 catalog 소유 테이블에 직접 접근하지 않는다.

---

## 3. 하이브리드 서비스 배치 기준

본 프로젝트는 AWS 채널계와 On-Prem 금융 업무계로 백엔드 역할을 분리한다.

### 3.1 AWS 배치 기준

```
AWS EKS
└── service-catalog   # 홈, 상품 조회, 카테고리, 장바구니, BNPL 결제요청 접수
```

AWS 배치 이유:
- 상품 조회와 장바구니는 사용자 접점의 채널계 트래픽이다
- CloudFront, ALB, WAF 등 AWS 외부 노출 계층과 자연스럽게 연결된다
- BNPL 결제요청 접수는 구매 의사를 받는 채널계 기능이며, 실제 금융 판단은 On-Prem service-core가 담당한다

### 3.2 On-Prem 배치 기준

```
On-Prem K8s
├── service-core    # 회원, 인증, 한도 신청, 외상 승인, 주문 확정, 지갑, 원장
├── service-batch   # BSS 산출, 이자 청구, 연체 감지
└── service-admin   # 관리자 심사, 감사 로그
```

On-Prem 배치 이유:
- 회원 인증과 개인정보(주민등록번호 해시, 휴대폰 번호)를 다룬다
- 한도 신청, 외상 승인, 주문 확정, 지갑, 원장은 금융 거래성 로직이다
- 주문과 한도 사용 내역은 정합성이 중요하므로 On-Prem에서 최종 처리한다
- service-core는 사용자 핵심 거래 요청을 처리하므로 KEDA + Prophet 기반 오토스케일링 검증의 주요 대상이 된다

### 3.3 service-batch K8s CronJob 구성

`service-batch`는 항상 떠있는 Deployment가 아니라 스케줄 시간에만 뜨는 CronJob으로 배포한다.

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: kkpp-batch-monthly-bss
spec:
  schedule: "0 1 1 * *"    # 매월 1일 새벽 1시
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: kkpp-batch
            image: kkpp/service-batch:latest
          restartPolicy: OnFailure
```

| Job | 스케줄 | 설명 |
|-----|--------|------|
| BSS 월별 산출 | `0 1 1 * *` | 매월 1일 새벽 1시 |
| BSS 연도별 산출 | `0 2 1 1 *` | 매년 1월 1일 새벽 2시 |
| 이자 청구 생성 | `0 3 1 * *` | 매월 1일 새벽 3시 |
| 연체 감지 | `0 0 * * *` | 매일 자정 |
