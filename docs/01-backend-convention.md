# 백엔드 코드 컨벤션

> 프로젝트: Kong Kong Farm (농업인 한도 기반 외상 구매 서비스)
> 스택: Spring Boot 3.3.4 · Java 21 · Gradle Groovy DSL · PostgreSQL · Kafka · Redis
> 팀: Dev6 · 5명 · 2개월

---

## 1. 네이밍 컨벤션

### 1.1 클래스

| 종류 | 규칙 | 예시 |
|------|------|------|
| Entity | 테이블명을 PascalCase | `UserAuth`, `CreditLimit`, `InterestLedger` |
| Repository | `{Entity명}Repository` | `UserAuthRepository` |
| Service | `{도메인명}Service` | `AuthService`, `CreditLimitService` |
| Controller | `{도메인명}Controller` | `AuthController` |
| Request DTO | `{행위}{대상}Request` | `LoginRequest`, `PinChangeRequest` |
| Response DTO | `{대상}Response` | `TokenResponse`, `CreditLimitResponse` |
| Mapper | `{Entity명}Mapper` | `UserAuthMapper` |
| Kafka Event | `{대상}{행위}Event` | `BnplPaymentRequestedEvent`, `OrderConfirmedEvent` |
| Exception | `{대상}{상황}Exception` | `UserNotFoundException`, `InsufficientLimitException` |
| Config | `{기능}Config` | `SecurityConfig`, `KafkaConfig` |
| Enum | PascalCase, 값은 UPPER_SNAKE_CASE | `OrderStatus.CONFIRMED` |

### 1.2 메서드

| 역할 | 접두사 | 예시 |
|------|--------|------|
| 단건 조회 | `get` | `getUser()`, `getCreditLimit()` |
| 목록 조회 | `get{복수}` | `getOrders()` |
| 존재 확인 | `exists` | `existsByPhone()` |
| 생성 | `create` | `createOrder()` |
| 수정 | `update` | `updatePinHash()` |
| 삭제/취소 | `cancel`, `delete` | `cancelOrder()` |
| 검증 | `validate` | `validatePin()` |
| Repository 쿼리 | Spring Data 네이밍 규칙 | `findByUserId()`, `findAllByStatus()` |

### 1.3 변수 / 필드

- camelCase 사용
- Boolean 필드: `is` 접두사 → `isRead`, `isReapplication`
- 내부 DB 식별자 필드: `{대상}Id` → `userId`, `creditLimitId`
- 외부/서비스 간 식별자 필드: `{대상}PublicId` → `userPublicId`, `orderPublicId`, `paymentRequestPublicId`
- 상수: `UPPER_SNAKE_CASE` → `MAX_RETRY_COUNT`

### 1.4 URL

| 규칙 | 예시 |
|------|------|
| 소문자 케밥케이스 | `/credit-limits`, `/farmer-documents` |
| 복수형 리소스 | `/orders`, `/products` |
| ID는 PathVariable | `/orders/{orderId}` |
| public_id 사용 (내부 id 노출 금지) | `/orders/{publicId}` |
| 버전 prefix | `/api/v1/orders` |

```
GET    /api/v1/orders              # 목록
GET    /api/v1/orders/{publicId}   # 단건
POST   /api/v1/orders              # 생성
PATCH  /api/v1/orders/{publicId}   # 부분 수정
DELETE /api/v1/orders/{publicId}   # 삭제/취소
```

---

## 2. API 응답 포맷

### 2.1 공통 응답 구조

모든 API는 `ApiResponse<T>`로 감싸서 반환한다.

```json
// 성공
{
  "success": true,
  "data": { },
  "error": null
}

// 실패
{
  "success": false,
  "data": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "존재하지 않는 사용자입니다."
  }
}
```

### 2.2 HTTP 상태코드

| 상황 | 코드 |
|------|------|
| 조회 성공 | 200 |
| 생성 성공 | 201 |
| 비즈니스 예외 (잘못된 요청) | 400 |
| 인증 실패 | 401 |
| 권한 없음 | 403 |
| 리소스 없음 | 404 |
| 서버 오류 | 500 |

### 2.3 외부 응답에서 내부 ID 노출 금지

```java
// ❌ 금지
public record OrderResponse(Long id, ...) {}

// ✅ 올바름
public record OrderResponse(UUID publicId, ...) {}
```

---

## 3. 예외 처리

### 3.1 예외 계층 구조

```
RuntimeException
└── BusinessException (common-core)
    ├── UserNotFoundException
    ├── InsufficientLimitException
    ├── InvalidPinException
    └── ...
```

### 3.2 ErrorCode enum 네이밍

```
{도메인}_{상황}

USER_NOT_FOUND
INVALID_PIN
INSUFFICIENT_CREDIT_LIMIT
ORDER_ALREADY_CANCELLED
WALLET_BALANCE_INSUFFICIENT
```

### 3.3 예외 발생 규칙

- Service 레이어에서만 예외를 던진다
- Controller에서 직접 예외를 던지지 않는다
- Repository에서 `Optional`을 반환하고 Service에서 `.orElseThrow()` 사용

```java
// ✅ 올바른 패턴
public UserAuth getUserAuth(UUID userPublicId) {
    return userAuthRepository.findByUserPublicId(userPublicId)
        .orElseThrow(() -> new UserNotFoundException(userPublicId));
}
```

---

## 4. Entity 작성 규칙

### 4.1 공통 규칙

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    // 연관관계는 지연 로딩 기본
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_public_id", referencedColumnName = "public_id")
    private User user;

    // Enum은 String으로 저장
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    // 생성은 정적 팩토리 메서드로
    public static Order create(User user, BigDecimal totalAmount) {
        Order order = new Order();
        order.publicId = UUID.randomUUID();
        order.user = user;
        order.totalAmount = totalAmount;
        order.orderStatus = OrderStatus.CREATED;
        return order;
    }

    // 상태 변경은 의미 있는 메서드로
    public void confirm() {
        this.orderStatus = OrderStatus.CONFIRMED;
    }
}
```

### 4.2 금지 사항

- `@Setter` 클래스 레벨 사용 금지 → 상태 변경은 의미 있는 메서드로
- `FetchType.EAGER` 사용 금지 → 항상 `LAZY`
- `@Data` 사용 금지 → `@Getter` + 필요한 어노테이션만
- 양방향 연관관계는 꼭 필요할 때만 → 단방향 우선
- 다른 서비스 소유 Entity/Repository 직접 import 금지 → publicId 저장 또는 HTTP/Kafka 연동 사용

### 4.3 BaseEntity 상속 기준

| 구분 | 상속 대상 | 해당 테이블 |
|------|-----------|-------------|
| `BaseEntity` | `created_at` + `updated_at` | users, orders, products, credit_limits 등 |
| `BaseTimeEntity` | `created_at` 만 | audit_logs, wallet_transactions, order_items, ass_scores, bss_scores |

---

## 5. DTO 작성 규칙

### 5.1 Java Record 사용

```java
// Request DTO
public record LoginRequest(
    @NotBlank String phone,
    @NotBlank @Size(min = 6, max = 6) String pin
) {}

// Response DTO
public record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
```

### 5.2 MapStruct로 변환

```java
@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderResponse toResponse(Order order);
    List<OrderResponse> toResponseList(List<Order> orders);
}
```

- Entity → Response DTO 변환은 MapStruct만 사용
- Controller나 Service에서 직접 `new ResponseDto(entity.getField())` 방식 금지

---

## 6. JWT 인증 규칙

### 6.1 토큰 스펙

| 항목 | 값 |
|------|-----|
| 알고리즘 | HS256 |
| 액세스 토큰 만료 | 1시간 |
| 리프레시 토큰 만료 | 30일 |
| 액세스 토큰 저장 | 클라이언트만 보관 (서버 DB 저장 없음) |
| 리프레시 토큰 저장 | DB(`user_auth.refresh_token`) + 클라이언트 |

### 6.2 요청 헤더

```
Authorization: Bearer {accessToken}
```

### 6.3 @AuthUser 사용

```java
@GetMapping("/me")
public ApiResponse<UserResponse> getMe(@AuthUser AuthUserInfo authUser) {
    return ApiResponse.success(userService.getUser(authUser.userPublicId()));
}
```

---

## 7. Kafka 이벤트 규칙

### 7.1 토픽 네이밍

```
{서비스}.{도메인}.{행위}

core.order.confirmed
core.limit.approved
core.interest.charged
batch.bss.calculated
batch.overdue.detected
```

### 7.2 이벤트 클래스

```java
// common-core의 event 패키지에 위치
// 모든 Kafka 이벤트는 Record 사용
public record BnplPaymentRequestedEvent(
    UUID eventId,
    UUID paymentRequestPublicId,
    UUID userPublicId,
    BigDecimal totalAmount,
    LocalDateTime occurredAt
) {}

public record OrderConfirmedEvent(
    UUID eventId,
    UUID orderPublicId,
    UUID paymentRequestPublicId,
    UUID userPublicId,
    BigDecimal amount,
    LocalDateTime occurredAt
) {}
```

### 7.3 규칙

- 이벤트 클래스는 `common-core`의 `event` 패키지에 위치
- Producer는 트랜잭션 커밋 후 발행 → `@TransactionalEventListener` 사용
- Consumer는 멱등성 보장 → 중복 수신 시 재처리 방지 로직 필수
- Kafka로 발행되는 서비스 간 이벤트에는 내부 `Long id`를 사용하지 않는다
- Kafka 이벤트는 `userPublicId`, `orderPublicId`, `paymentRequestPublicId`, `creditLimitPublicId` 같은 `publicId(UUID)` 기준으로 작성한다
- 단일 서비스 내부에서만 사용하는 도메인 이벤트는 내부 id 사용이 가능하지만, Kafka로 외부 발행되는 이벤트는 publicId 기준을 따른다

---

## 8. 트랜잭션 경계 규칙

### 8.1 서비스 내부 트랜잭션

같은 서비스 안의 작업은 `@Transactional`로 묶는다.

```java
// ✅ 하나의 트랜잭션 — 주문 생성 + 한도 사용 원장 기록은 같은 service-core 안
@Transactional
public OrderResponse createOrder(UUID userPublicId, CreateOrderRequest request) {
    Order order = orderRepository.save(Order.create(...));
    creditUsageLedgerRepository.save(CreditUsageLedger.create(order, ...));
    return orderMapper.toResponse(order);
}
```

### 8.2 서비스 간 트랜잭션

서비스 간에는 단일 트랜잭션이 불가능하다. Kafka 이벤트 + 멱등성으로 정합성을 보장한다.

```java
// ✅ 트랜잭션 커밋 후 이벤트 발행
@Transactional
public void confirmOrder(UUID orderPublicId) {
    Order order = orderRepository.findByPublicId(orderPublicId)...;
    order.confirm();
    eventPublisher.publishEvent(new OrderConfirmedEvent(order));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderConfirmed(OrderConfirmedEvent event) {
    kafkaTemplate.send("core.order.confirmed", event);
}
```

### 8.3 Consumer 멱등성 보장

```java
// ✅ 이미 처리된 이벤트는 건너뜀
@KafkaListener(topics = "core.order.confirmed")
public void handleOrderConfirmed(OrderConfirmedEvent event) {
    if (creditUsageLedgerRepository.existsByOrderPublicId(event.orderPublicId())) {
        return;
    }
    creditUsageLedgerRepository.save(...);
}
```

---

## 9. 주의사항 (자주 실수하는 것)

### Spring Batch 5.x API 변경

```java
// ❌ 4.x 방식 — 컴파일 에러
@Autowired JobBuilderFactory jobBuilderFactory;

// ✅ 5.x 방식
@Bean
public Job bssMonthlyJob(JobRepository jobRepository, Step step) {
    return new JobBuilder("bssMonthlyJob", jobRepository)
        .start(step)
        .build();
}
```

### jjwt 0.12.x API 변경

```java
// ❌ 0.11.x 방식 — 컴파일 에러
Jwts.builder().signWith(key, SignatureAlgorithm.HS256)

// ✅ 0.12.x 방식
Jwts.builder().signWith(secretKey)
```

### Lombok + MapStruct annotationProcessor 순서

```groovy
// build.gradle — Lombok 반드시 먼저
annotationProcessor "org.projectlombok:lombok:${lombokVersion}"
annotationProcessor "org.mapstruct:mapstruct-processor:${mapstructVersion}"
```

### AWS SDK 버전

```groovy
// ❌ SDK v1 — 절대 사용 금지
implementation 'com.amazonaws:aws-java-sdk-s3'

// ✅ SDK v2 (spring-cloud-aws 3.x 호환)
implementation 'io.awspring.cloud:spring-cloud-aws-s3'
```
