# 도메인 규칙

> 프로젝트: Kong Kong Farm (농업인 한도 기반 외상 구매 서비스)
> 팀: Dev6 · 5명 · 2개월

---

## 1. Redis 사용 규칙

Redis는 영구 저장소가 아니라 TTL 기반 임시 데이터와 캐시에만 사용한다.

### 1.1 사용 용도

| 용도 | 서비스 | Redis 위치 | TTL |
|------|--------|-----------|-----|
| 휴대폰 인증 코드 | service-core | On-Prem Redis | 3분 |
| PIN 실패 횟수 | service-core | On-Prem Redis | 30분 |
| refresh_token 블랙리스트 | service-core | On-Prem Redis | 토큰 만료시간과 동일 |
| 한도 심사 신청 Draft | service-core | On-Prem Redis | 1시간 |
| 한도 조회 캐시 | service-core | On-Prem Redis | 10분 |
| 장바구니 조회 캐시 | service-catalog | AWS ElastiCache | 30분 |

장바구니 원본 데이터는 `cart_items` 테이블에 저장한다.

local/dev에서는 단일 PostgreSQL 인스턴스를 사용하지만, 서비스별 테이블 소유권은 분리해서 관리한다.

목표 구조에서는 AWS Catalog DB의 `cart_items`가 장바구니 원본이 된다.

AWS ElastiCache Redis는 장바구니 조회 성능 향상을 위한 캐시로 사용한다. Redis 장애 또는 캐시 미스가 발생하면 `cart_items` 테이블을 기준으로 복구한다.

### 1.2 Key Prefix 규칙

서비스 간 식별자 공유는 내부 Long id가 아니라 `public_id(UUID)`를 기준으로 한다. 따라서 Redis key에도 `userId`가 아닌 `userPublicId`를 사용한다.

```
auth:phone-code:{phone}
auth:pin-fail-count:{userPublicId}
auth:refresh-blacklist:{tokenId}

credit:application:draft:{sessionId}
credit:application:session:{sessionId}
credit:limit-cache:{userPublicId}

cart:items:{userPublicId}    # 장바구니 원본이 아니라 cart_items 조회 결과 캐시
```

### 1.3 Redis 사용 원칙

- 영구 보관 데이터는 Redis에만 저장하지 않는다
- 모든 Redis 데이터에 TTL을 반드시 설정한다
- 한도, 원장, 주문의 최종 데이터는 PostgreSQL 기준으로 처리한다
- Redis 장애 시 핵심 거래 데이터가 유실되지 않도록 설계한다

```java
// ✅ TTL 필수 설정
redisTemplate.opsForValue().set(
    "auth:phone-code:" + phone,
    code,
    Duration.ofMinutes(3)
);

// ❌ TTL 없는 저장 금지
redisTemplate.opsForValue().set("auth:phone-code:" + phone, code);
```

---

## 2. 한도 심사 Draft 저장 규칙

한도 심사 신청은 단계별 입력 구조를 사용한다.

```
농지 정보 → 작물 정보 → 보험 정보 → 서류 업로드 → 최종 제출
```

K8s 환경에서 Pod 재시작 또는 Scale-out 시 서버 메모리 데이터가 유실될 수 있으므로, 단계별 임시 데이터는 Redis에 저장한다.

### 2.1 Redis Key 및 TTL

```
Key: credit:application:draft:{sessionId}
TTL: 1시간
```

### 2.2 처리 흐름

```
신청 시작
→ Redis Draft 생성 (TTL 1시간)

각 단계 입력 (농지/작물/보험/서류)
→ Redis Draft 갱신 (TTL 갱신)

최종 제출
→ Redis Draft 조회
→ 필수 입력값 전체 검증
→ PostgreSQL 저장 (credit_limit_applications)
→ Redis Draft 삭제
```

### 2.3 주의사항

- 최종 제출 전까지는 정식 한도 신청 데이터가 아니다
- 최종 제출 시점에 모든 필수 입력값을 재검증한다
- Redis Draft가 만료된 경우 사용자는 신청을 처음부터 다시 시작해야 한다
- 서류 파일(farmer_documents)은 각 단계에서 S3에 즉시 업로드하고, Draft에는 URL만 저장한다

---

## 3. 장바구니, BNPL 결제요청, 주문 처리 원칙

장바구니와 BNPL 결제요청 접수는 `service-catalog`에서 처리하고, 실제 금융 검증과 주문 확정은 `service-core`에서 처리한다.

### 3.1 장바구니 (service-catalog / AWS EKS)

아래 URI는 도메인 흐름 이해를 위한 예시이며, 상세 요청/응답 필드는 API 명세서를 기준으로 한다.

```
장바구니 담기     POST   /api/v1/cart/items
수량 변경         PATCH  /api/v1/cart/items/{cartItemPublicId}
삭제             DELETE /api/v1/cart/items/{cartItemPublicId}
장바구니 조회     GET    /api/v1/cart/items
```

장바구니는 service-catalog에서 처리하며, 원본은 `cart_items` 테이블에 저장한다. AWS ElastiCache Redis는 장바구니 조회 캐시로만 사용한다. `cart_items` 테이블을 사용하므로 장바구니 항목 수정/삭제는 `cartItemPublicId` 기준으로 처리한다.

### 3.2 BNPL 결제요청 접수 (service-catalog / AWS EKS)

사용자가 장바구니에서 외상구매를 요청하면 service-catalog는 결제요청을 접수하고 `bnpl_payment_requests`를 `REQUESTED` 상태로 생성한다. 이 단계는 구매 의사 접수이며, 한도 검증이나 원장 반영을 수행하지 않는다.

```
BNPL 결제요청 생성      POST /api/v1/bnpl-requests
최근 결제요청 조회       GET  /api/v1/bnpl-requests/recent
결제요청 상세 조회       GET  /api/v1/bnpl-requests/{paymentRequestPublicId}
```

처리 흐름:

```
1. JWT에서 userPublicId 확인
2. 장바구니 항목 조회
3. 상품명, 단가, 수량을 bnpl_payment_request_items에 스냅샷으로 저장
4. bnpl_payment_requests 상태를 REQUESTED로 저장
5. BnplPaymentRequestedEvent 발행
6. service-core가 이벤트를 수신하여 금융 검증 수행
```

`bnpl_payment_requests`와 `bnpl_payment_request_items`는 service-catalog가 관리하는 결제요청 원본 테이블이다.

service-core는 해당 테이블을 직접 조회하지 않는다.

service-core는 service-catalog가 발행한 Kafka 이벤트의 `paymentRequestPublicId` 또는 `checkoutRequestId`를 BNPL 결제요청의 public_id로 보고, 한도 검증 및 원장 생성 시 `payment_request_public_id`로 연결한다.

### 3.3 주문 확정 (service-core / On-Prem K8s)

주문은 사용자가 직접 생성하는 최종 데이터가 아니라, BNPL 결제요청을 service-core가 검증하고 승인한 뒤 생성되는 확정 데이터이다.

내부 처리 또는 조회 API 예시:

```
주문 상세 조회    GET    /api/v1/orders/{orderPublicId}
주문 취소         PATCH  /api/v1/orders/{orderPublicId}/cancel
```

### 3.4 주문 확정 시 재검증 순서

service-core가 `BnplPaymentRequestedEvent`를 수신하면 다음 순서로 반드시 재검증한다.

```
1. userPublicId 기준 사용자 상태 확인
2. 결제요청 상품 스냅샷 확인
3. 필요 시 service-catalog API로 상품 판매 상태 확인 (ON_SALE 여부)
4. 필요 시 service-catalog API로 현재 가격/재고 확인
5. 사용자 한도 확인 (credit_limits.total_limit - used_amount >= 주문 금액)
6. 주문 확정 (orders, order_items)
7. 한도 사용 내역 기록 (credit_usage_ledger)
8. 원금상환원장 생성 및 이자 청구 대상 반영
9. BNPL 승인/반려 이벤트 발행
```

AWS 장바구니와 결제요청 데이터는 주문 확정 데이터가 아니다. On-Prem service-core의 검증과 승인 결과가 최종 기준이다.

### 3.5 원장 생성 및 이자 청구 기준

BNPL 구매 확정 시 `service-core`는 주문 확정과 함께 한도 사용 및 원금 상환 기준 데이터를 생성한다.
BNPL 결제요청 승인
→ orders 생성
→ order_items 생성
→ credit_limits.used_amount 증가
→ credit_usage_ledger INSERT
→ principal_repayment_ledger INSERT


`principal_repayment_ledger`는 BNPL 구매 확정 건별 원금 상환 예정 및 납부 상태를 저장한다.  
따라서 BNPL 구매가 여러 번 발생하면 원금상환원장도 구매 건별로 여러 건 생성될 수 있다.

`interest_ledger`는 BNPL 구매 시점에 직접 생성하지 않는다.  
월별 이자 청구는 `service-batch`의 interest 배치가 `credit_limits.used_amount`를 기준으로 생성한다.


월별 이자 청구 배치
→ ACTIVE 상태의 credit_limits 조회
→ used_amount > 0인 한도만 대상
→ base_principal = used_amount
→ interest_rate 기준 월 이자 계산
→ interest_ledger INSERT


동일 한도와 동일 납부 예정일에 이자 원장이 중복 생성되지 않도록 `credit_limit_public_id + due_date` 기준으로 중복 생성을 방지한다.

원금 상환 시에는 `principal_repayment_ledger.amount_paid`를 갱신하고, `credit_limits.used_amount`를 함께 감소시킨다.  
이자 상환 시에는 `interest_ledger.amount_paid`를 갱신한다.  
실제 지갑 잔액 변동 이력은 `wallet_transactions`에 저장한다.

BNPL 결제요청 Kafka 이벤트는 중복 수신될 수 있으므로, service-core는 `payment_event_process_logs`를 사용하여 동일 이벤트 또는 동일 결제요청이 중복 처리되지 않도록 멱등성을 보장한다.
