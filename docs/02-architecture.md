# 백엔드 아키텍처 설계

> 프로젝트: Kong Kong Farm (농업인 한도 기반 외상 구매 서비스)
> 스택: Spring Boot 3.3.4 · Java 21 · Gradle Groovy DSL · PostgreSQL · Kafka · Redis
> 팀: Dev6 · 5명 · 2개월

---

## 1. 프로젝트 구조

### 1.1 모듈 구성

과도한 MSA 분리를 지양하고, 실제 배포 단위 기준으로 4개 서비스로 구성한다.

```
kkpp-backend/
├── common-core        # BaseEntity, 공통 예외, 응답 포맷, enum, event
├── common-security    # JWT 검증 필터, @AuthUser
│
├── service-catalog    # AWS 배포 — 홈, 상품 조회, 카테고리, 장바구니
├── service-core       # On-Prem 배포 — 회원, 인증, 외상 신청, 한도, 주문, 지갑
├── service-batch      # On-Prem 배포 — BSS 월별/연도별 산출, 이자 청구, 연체 감지
└── service-admin      # On-Prem 배포 — 관리자 인증, 한도 심사, 상품/주문/연체 관리
```

각 `service-*`는 독립 배포 단위이며, 별도 Docker 이미지와 K8s 리소스를 가진다.

| 서비스 | 배치 위치 | K8s 리소스 | 역할 |
|--------|----------|-----------|------|
| `service-catalog` | AWS EKS | Deployment | 홈, 상품 조회, 카테고리, 장바구니 |
| `service-core` | On-Prem K8s | Deployment | 회원, 인증, 외상 신청, 한도, 주문, 지갑 |
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

## 2. MSA 서비스 경계 규칙

### 2.1 서비스 경계

| 서비스 | 배치 위치 | 역할 |
|--------|----------|------|
| `service-catalog` | AWS EKS | 홈, 상품 조회, 카테고리, 장바구니 |
| `service-core` | On-Prem K8s | 회원, 인증, 외상 신청, 한도, 주문, 지갑 |
| `service-batch` | On-Prem K8s | BSS 산출, 이자 청구, 연체 감지 |
| `service-admin` | On-Prem K8s | 관리자 인증, 한도 심사, 상품/주문/연체 관리 |

### 2.2 서비스별 DB 스키마 분리

하나의 PostgreSQL 인스턴스를 사용하되, 서비스별로 스키마를 분리한다.

| 스키마 | 담당 서비스 | 포함 테이블 |
|--------|------------|------------|
| `catalog` | service-catalog | products, categories, cart_items |
| `core` | service-core, service-batch | users, user_auth, farmer_profiles, farmer_documents, credit_limit_applications, credit_limits, ass_scores, bss_scores, orders, order_items, credit_usage_ledger, interest_ledger, principal_repayment_ledger, loan_overdue_ledger, wallets, wallet_transactions |
| `admin` | service-admin | admin_users, audit_logs, notifications |

> `service-batch`는 `service-core`의 업무 데이터를 대상으로 하는 배치 실행 모듈이므로 `core` 스키마 접근을 허용한다. 단, `service-batch`는 사용자 요청 API를 제공하지 않고 정해진 스케줄에 따라 실행되는 K8s CronJob으로만 동작한다.

Redis는 인스턴스를 구분한다.

| Redis 위치 | 담당 서비스 | 용도 |
|-----------|------------|------|
| On-Prem Redis | service-core | refresh_token 블랙리스트, PIN 실패, 인증 코드, 한도 Draft, 한도 캐시 |
| AWS ElastiCache | service-catalog | 장바구니 조회 캐시 |

장바구니 원본은 `catalog.cart_items` 테이블에 저장하고, AWS ElastiCache Redis는 조회 성능 향상을 위한 캐시로 사용한다.

### 2.3 크로스 스키마 접근 금지

다른 서비스의 스키마 테이블에 직접 접근하는 것을 금지한다. JPA, JDBC, 네이티브 쿼리 모두 해당된다.

```java
// ❌ 절대 금지 — service-core에서 catalog 스키마 직접 접근
@Query("SELECT o, p FROM Order o JOIN Product p ON o.productId = p.id")
List<Object[]> getOrdersWithProduct();

// ✅ 올바른 방법
// 주문 생성 시 상품 정보가 필요하면 service-catalog API를 HTTP로 호출
// 또는 주문 당시 상품 정보를 order_items에 스냅샷으로 저장
```

### 2.4 서비스 간 통신 기준

같은 서비스 내부 도메인 간 처리는 메서드 호출로 처리한다.

| 상황 | 방식 | 예시 |
|------|------|------|
| 즉시 결과가 필요한 조회 | HTTP | 주문 생성 시 상품 가격/재고 확인 |
| 데이터 변경 후속 처리 | Kafka | 주문 확정 후 한도 사용 원장 기록 |
| 배치 완료 후속 처리 | Kafka | BSS 산출 완료 후 알림 발송 |
| 알림 발송 | Kafka | 한도 승인/반려 알림 |

### 2.5 상품과 주문의 경계

상품 데이터의 소유권은 `service-catalog`에 둔다.

```
상품 조회        → service-catalog
장바구니         → service-catalog, catalog.cart_items 원본 + AWS ElastiCache 캐시
주문 생성        → service-core
상품 등록/수정   → service-admin → service-catalog 내부 API 호출 → catalog.products 저장
```

`service-admin`은 관리자 인증과 권한 검증 후 상품 관리 요청을 `service-catalog`로 전달한다. `service-admin`은 `catalog` 스키마에 직접 접근하지 않는다.

---

## 3. 하이브리드 서비스 배치 기준

본 프로젝트는 AWS 채널계와 On-Prem 업무계로 백엔드 역할을 분리한다.

### 3.1 AWS 배치 기준

```
AWS EKS
└── service-catalog   # 홈, 상품 조회, 카테고리, 장바구니
```

AWS 배치 이유:
- 상품 조회는 읽기 트래픽이 많고 공개성이 높다
- CloudFront, ALB, WAF 등 AWS 외부 노출 계층과 자연스럽게 연결된다
- service-catalog는 AWS 채널계 조회 서비스로 상품 조회와 장바구니 요청을 처리한다

### 3.2 On-Prem 배치 기준

```
On-Prem K8s
├── service-core    # 회원, 인증, 외상 신청, 한도, 주문, 지갑
├── service-batch   # BSS 산출, 이자 청구, 연체 감지
└── service-admin   # 관리자 심사, 감사 로그
```

On-Prem 배치 이유:
- 회원 인증과 개인정보(주민등록번호 해시, 휴대폰 번호)를 다룬다
- 외상 신청, 한도, 주문은 금융 거래성 로직이다
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
