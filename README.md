# 🌾 FISA-Agri-Pay · Back-end

> 농업 데이터 기반 **BNPL(Buy Now, Pay Later) 플랫폼**의 백엔드.
> 금융 핵심 도메인을 책임별로 분리한 **Spring Boot 멀티모듈 MSA**입니다.

![Java](https://img.shields.io/badge/Java%2021-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Batch](https://img.shields.io/badge/Spring%20Batch-6DB33F?style=flat-square&logo=spring&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)
![AWS SQS](https://img.shields.io/badge/AWS%20SQS-FF4F8B?style=flat-square&logo=amazonsqs&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)

---

## 📌 개요

농업인은 영농 주기(파종·생육·수확)에 따라 소득 시점이 달라 **현금흐름 불일치**를 겪지만, 기존 금융권은 이런 계절성 소득 구조를 반영하기 어렵습니다. 본 백엔드는 농업 데이터(농지·작물·보험·상환 이력) 기반 **대안신용평가 BNPL 서비스**의 핵심 금융 로직을 담당합니다.

> 전체 프로젝트(하이브리드 클라우드 · 예측 기반 오토스케일링 · Observability) 문서는 [조직 프로필](https://github.com/FISA-Agri-Pay)을 참고하세요.

---

## 🏗️ 모듈 구성

멀티모듈 Gradle 프로젝트로, 공통 모듈과 서비스 모듈로 나뉩니다.

| 모듈 | 구분 | 책임 |
| --- | --- | --- |
| `service-auth` | 서비스 | 인증 · 인가 (Spring Security · JWT) |
| `service-core` | 서비스 | 대안신용평가 · 신용 한도 등 핵심 금융 도메인 |
| `service-catalog` | 서비스 | 상품 · 체크아웃, 결제 이벤트(SQS) 발행 |
| `service-payment` | 서비스 | 결제 이벤트 소비 · 멱등 처리 · 한도 차감 · 이용 원장 기록 |
| `service-admin` | 서비스 | 관리자 · 신용 심사 (CQRS 읽기/쓰기 분리) |
| `service-batch` | 서비스 | 이자 · 자동 상환 · 연체 배치 |
| `common-core` | 공통 | 공통 도메인 · 유틸 · 설정 |
| `common-security` | 공통 | 공통 보안 (JWT · 인증 필터 등) |

---

## 🔍 핵심 기능 구현

### 1. 농업 데이터 기반 대안신용평가

* **대안신용평가** : 급여·금융거래 이력 대신 농지·작물·보험·영농 경력 등 농업 데이터로 농업인 맞춤 한도를 산정
* **신용 심사 워크플로우** : 신청 → 심사 → 승인/한도 부여 → 한도 기반 BNPL 이용
* **계절성 반영** : 영농 주기로 인한 현금흐름 불일치를 고려한 한도·상환 설계

```java
// 농지 면적·작물·보험·영농 경력을 조합한 대안신용점수 산정
BigDecimal estimatedIncome = calculateEstimatedIncome(profile.getFieldAreaM2(), cropType);
int incomeScore = calculateIncomeScore(estimatedIncome);
int insuranceScore = calculateInsuranceScore(profile.getHasCropInsurance());
int farmingCareerScore = calculateFarmingCareerScore(profile);
int totalScore = incomeScore + insuranceScore + farmingCareerScore;

return new AssScoreResult(
        estimatedIncome, LocalDate.now(),
        incomeScore, insuranceScore, farmingCareerScore, totalScore,
        LocalDateTime.now()
);
```

* [`service-core/.../credit/service/AssScoringService.java`](service-core/src/main/java/com/kkpp/core/credit/service/AssScoringService.java)
* [`service-core/.../credit/service/CreditSubmitPersistenceService.java`](service-core/src/main/java/com/kkpp/core/credit/service/CreditSubmitPersistenceService.java)
* [`service-admin/.../credit/service/CreditReviewService.java`](service-admin/src/main/java/com/kkpp/admin/credit/service/CreditReviewService.java)

---

### 2. 이벤트 기반 BNPL 결제

* **이벤트 흐름** : 체크아웃 → 결제 요청 저장 → SQS 발행 → 결제 서비스가 소비해 한도 차감·주문 생성·이용 원장 기록
* **멱등 처리** : SQS는 at-least-once 전달이므로, 처리 이력 테이블(`paymentEventProcessLog`)에 이벤트 ID·결제요청 ID를 기록해 동일 결제의 중복 반영을 차단
* **분산 추적** : SQS 메시지 attribute에 OpenTelemetry trace context를 실어 보내고 소비 시 복원 → 체크아웃 → SQS → 결제 소비를 end-to-end 추적

```java
// 결제 요청 저장 후 SQS 이벤트 발행
BnplPaymentRequest paymentRequest = bnplPaymentRequestRepository.saveAndFlush(BnplPaymentRequest.create(
        paymentRequestPublicId, userPublicId, totalAmount, cartItems
));
UUID orderPublicId = orderPublicId(paymentRequest.getPublicId());
creditPaymentEventProducer.publish(
        toEvent(paymentRequest, orderPublicId, cartItems, request.deliveryAddress(), request.idempotencyKey())
);
```

```java
// SQS 메시지 발행 시 trace context 전파
SendMessageRequest request = SendMessageRequest.builder()
        .queueUrl(paymentRequestQueueUrl)
        .messageBody(payload)
        .messageGroupId(event.userPublicId().toString())
        .messageDeduplicationId(event.paymentRequestPublicId().toString())
        .messageAttributes(SqsTraceContext.currentMessageAttributes())
        .build();
```

```java
// 이벤트 소비 시 멱등 처리 + 주문 생성 + 한도 차감 + 이용 원장 기록
if (paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(eventId, paymentRequestPublicId)) {
    return;
}
order = orderRepository.save(Order.confirmed(
        orderPublicId, userPublicId, paymentRequestPublicId,
        message.totalAmount(), message.deliveryAddress(), message.items(),
        Objects.requireNonNullElse(message.occurredAt(), LocalDateTime.now())
));
creditLimit.use(message.totalAmount());
creditUsageLedgerRepository.save(CreditUsageLedger.purchase(
        creditLimit.getPublicId(), order.getPublicId(), paymentRequestPublicId, message.totalAmount(), usedAt
));
paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
        eventId, paymentRequestPublicId, message.idempotencyKey()
));
```

* [`service-catalog/.../checkout/service/CheckoutService.java`](service-catalog/src/main/java/com/kkpp/catalog/checkout/service/CheckoutService.java)
* [`service-catalog/.../checkout/event/SqsCreditPaymentEventProducer.java`](service-catalog/src/main/java/com/kkpp/catalog/checkout/event/SqsCreditPaymentEventProducer.java)
* [`service-payment/.../event/SqsCreditPaymentRequestedConsumer.java`](service-payment/src/main/java/com/kkpp/payment/event/SqsCreditPaymentRequestedConsumer.java)
* [`service-payment/.../service/CreditPaymentProcessingService.java`](service-payment/src/main/java/com/kkpp/payment/service/CreditPaymentProcessingService.java)
* [`service-payment/.../domain/PaymentEventProcessLog.java`](service-payment/src/main/java/com/kkpp/payment/domain/PaymentEventProcessLog.java)

---

### 3. 배치 처리 (이자 · 자동 상환 · 연체)

* **스케줄 배치** : Spring Batch로 이자 계산 · 자동 상환 · 연체 처리를 주기적으로 실행
* **청크 기반 처리** : 대량 상환/원장 데이터를 Chunk(읽기-처리-쓰기) 단위로 나눠 대용량에도 안정적으로 처리
* **재처리 안전성** : Job 실행 메타데이터로 중복 실행을 방지하고, 실패 Step만 재시도

```java
// 이자 원장 생성을 100건 단위 Chunk로 분할 처리
return new StepBuilder("interestChargeMonthlyStep", jobRepository)
        .<CreditLimit, InterestLedger>chunk(CHUNK_SIZE, transactionManager)
        .reader(interestChargeMonthlyReader)
        .processor(interestChargeMonthlyProcessor)
        .writer(interestChargeMonthlyWriter)
        .build();
```

* [`service-batch/.../interest/job/InterestChargeMonthlyJobConfig.java`](service-batch/src/main/java/com/kkpp/batch/interest/job/InterestChargeMonthlyJobConfig.java)
* [`service-batch/.../interest/payment/job/InterestAutoPaymentJobConfig.java`](service-batch/src/main/java/com/kkpp/batch/interest/payment/job/InterestAutoPaymentJobConfig.java)
* [`service-batch/.../principal/repayment/job/PrincipalAutoPaymentJobConfig.java`](service-batch/src/main/java/com/kkpp/batch/principal/repayment/job/PrincipalAutoPaymentJobConfig.java)
* [`service-batch/.../overdue/job/OverdueDetectionJobConfig.java`](service-batch/src/main/java/com/kkpp/batch/overdue/job/OverdueDetectionJobConfig.java)

---

### 4. CQRS 기반 읽기/쓰기 분리 (성능 개선)

* **패턴** : 변경(쓰기)과 조회(읽기)의 모델·책임을 분리하는 CQRS 적용
* **DB 분리** : PostgreSQL Primary(쓰기)/Replica(읽기)로 나눠 조회 부하를 Replica로 분산
* **검증** : k6 부하 테스트(100 RPS, 10분)로 응답 시간·처리 안정성 개선 확인

| 지표 | 적용 전 | 적용 후 | 차이 |
| --- | --- | --- | --- |
| 평균 응답 시간 | 50.48ms | 35.60ms | 29.5% 감소 |
| p95 응답 시간 | 68.05ms | 51.28ms | 24.6% 감소 |
| 최대 응답 시간 | 4.01s | 1.38s | 65.6% 감소 |
| 실패율 | 0% | 0% | - |
| Dropped Iterations | 304건 | 21건 | 93.1% 감소 |

```java
// core DB용 Repository / EntityManager / TransactionManager 분리
@EnableJpaRepositories(
    basePackages = {
        "com.kkpp.admin.adminauth.repository",
        "com.kkpp.admin.bnpl.repository",
        "com.kkpp.admin.order.repository",
        "com.kkpp.admin.credit.repository"
    },
    entityManagerFactoryRef = "coreEntityManagerFactory",
    transactionManagerRef = "coreTransactionManager"
)
```

```java
// 조회 모델: readOnly 트랜잭션 + DTO Projection 조회
@Transactional(readOnly = true)
public CreditReviewPageResponse getReviews(CreditReviewStatus status, int page, int size) {
    Page<CreditReviewSummaryResponse> result =
            applicationRepository.findReviewSummaries(status, pageable);
    return new CreditReviewPageResponse(
            result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast()
    );
}
```

```java
// 쓰기 모델: 별도 쓰기 트랜잭션 + 비관적 락
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select application from CreditReviewApplication application join fetch application.user where application.publicId = :publicId")
Optional<CreditReviewApplication> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
```

* [`service-admin/.../global/config/CoreDataSourceConfig.java`](service-admin/src/main/java/com/kkpp/admin/global/config/CoreDataSourceConfig.java)
* [`service-admin/.../global/config/CatalogDataSourceConfig.java`](service-admin/src/main/java/com/kkpp/admin/global/config/CatalogDataSourceConfig.java)
* [`service-admin/.../credit/service/CreditReviewService.java`](service-admin/src/main/java/com/kkpp/admin/credit/service/CreditReviewService.java)
* [`service-admin/.../credit/repository/CreditReviewApplicationRepository.java`](service-admin/src/main/java/com/kkpp/admin/credit/repository/CreditReviewApplicationRepository.java)
* [`service-admin/.../bnpl/service/BnplAdminService.java`](service-admin/src/main/java/com/kkpp/admin/bnpl/service/BnplAdminService.java)

---

### 5. CI/CD 빌드 최적화 (성능 개선)

* **이미지 최적화** : 멀티스테이지 빌드 + 경량 런타임 베이스 + 취약 베이스·불필요 패키지 제거 → 이미지 크기·보안 취약점 감소 (Trivy 스캔 취약점 0)
* **빌드 속도** : Docker 내부 중복 Gradle 빌드 제거(Jenkins가 생성한 JAR만 패키징) + Spring Boot Layered JAR로 의존성 레이어를 Harbor에 캐싱하여 단축

| 이미지 | 적용 전 | 적용 후 | 비교 |
| --- | --- | --- | --- |
| 이미지 크기 | 842MB | 694MB | 17.5% 감소 |
| 취약점 개수 | 128 | 0 | 100% 감소 |
| 레이어 개수 | 48 | 32 | 33.3% 감소 |

| 파이프라인 | 적용 전 | 적용 후 | 비교 |
| --- | --- | --- | --- |
| Docker Build & Push | 2분 5초 | 45초 | 약 64% 단축 |
| 전체 파이프라인 | 5분 27초 | 3분 10초 | 약 42% 단축 |

```dockerfile
# 개선된 Dockerfile — JAR은 Jenkins 호스트에서 빌드되어 들어오고, 이미지는 패키징만 (Docker 내 Gradle 재빌드 제거)
ARG RUNTIME_IMAGE=bellsoft/liberica-runtime-container:jre-21-slim-musl

FROM ${RUNTIME_IMAGE} AS extractor
WORKDIR /workspace
COPY build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${RUNTIME_IMAGE}
WORKDIR /app
# 변경 빈도가 낮은 레이어부터 복사 → 의존성 레이어 캐시 재사용, push 시 app 레이어만 전송
COPY --from=extractor --chown=65532:65532 /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=65532:65532 /workspace/extracted/application/ ./
```

* [`service-admin/Dockerfile`](service-admin/Dockerfile)
* [`service-auth/Dockerfile`](service-auth/Dockerfile)
* [`service-batch/Dockerfile`](service-batch/Dockerfile)
* [`service-catalog/Dockerfile`](service-catalog/Dockerfile)
* [`service-core/Dockerfile`](service-core/Dockerfile)
* [`service-payment/Dockerfile`](service-payment/Dockerfile)

---

## 🛠️ 기술 스택

| 영역 | 스택 |
| --- | --- |
| Language / Framework | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Spring Batch |
| Data / Event | PostgreSQL, Redis, AWS SQS |
| Observability | OpenTelemetry |
| Build / Container | Gradle (multi-module), Docker |
| Test | JUnit 5, Mockito, JaCoCo, k6 |

---

## 🚀 빌드 & 실행

### 요구사항
* JDK 21
* Docker / Docker Compose

### 1. 로컬 인프라 기동
```bash
docker compose -f docker-compose.local.yml up -d
```

### 2. 서비스 실행
```bash
# 예: 인증 서비스 실행
./gradlew :service-auth:bootRun

# 예: 결제 서비스 JAR 빌드
./gradlew :service-payment:bootJar
```

> 환경 변수(DB·Redis·SQS 접속 정보 등)는 각 서비스의 설정 파일을 참고하세요.
> <!-- TODO: 실제 포트 / 필수 환경 변수 / 프로파일(application-*.yml) 안내 추가 -->

---

## 📂 디렉터리 구조

```
back-end/
├─ common-core/         # 공통 도메인·유틸
├─ common-security/     # 공통 보안(JWT 등)
├─ service-auth/        # 인증·인가
├─ service-core/        # 대안신용평가·핵심 금융 도메인
├─ service-catalog/     # 상품·체크아웃(결제 이벤트 발행)
├─ service-payment/     # 결제 소비·멱등·한도 차감
├─ service-admin/       # 관리자·신용 심사(CQRS)
├─ service-batch/       # 이자·자동 상환·연체 배치
├─ docker/              # Dockerfile·빌드 리소스
├─ docs/                # 설계 문서
├─ docker-compose.local.yml
├─ settings.gradle      # 멀티모듈 설정
└─ build.gradle
```

---

## 🔗 관련 레포지토리

| 레포 | 설명 |
| --- | --- |
| [`back-end`](https://github.com/FISA-Agri-Pay/back-end) | 금융 핵심 도메인 백엔드 (현재 레포) |
| [`front-end`](https://github.com/FISA-Agri-Pay/front-end) | 사용자용 웹앱 프론트엔드 |
| [`front-end-admin`](https://github.com/FISA-Agri-Pay/front-end-admin) | 관리자용 웹 프론트엔드 |
| [`ai-prediction-model`](https://github.com/FISA-Agri-Pay/ai-prediction-model) | 시계열 예측 모델 · 오토스케일링 정책 |
| [`mcp-aiops-backend`](https://github.com/FISA-Agri-Pay/mcp-aiops-backend) | FastMCP 기반 AIOps 백엔드 |
| [`infra`](https://github.com/FISA-Agri-Pay/infra) | Terraform 기반 IaC · 운영 스크립트 |
| [`git-ops`](https://github.com/FISA-Agri-Pay/git-ops) | ArgoCD GitOps 배포 매니페스트 |

---

## 👥 Team

우리FISA 6기 · 클라우드 엔지니어링 과정 3팀 — 류승환(PM) · 이승준(PL) · 이동욱 · 사재헌 · 양규리
팀원 상세 소개는 [조직 프로필](https://github.com/FISA-Agri-Pay)을 참고하세요.
