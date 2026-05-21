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

장바구니 원본 데이터는 `catalog.cart_items` 테이블에 저장한다. AWS ElastiCache Redis는 장바구니 조회 성능 향상을 위한 캐시로 사용한다. Redis 장애 또는 캐시 미스가 발생하면 `catalog.cart_items` 테이블을 기준으로 복구한다.

### 1.2 Key Prefix 규칙

```
auth:phone-code:{phone}
auth:pin-fail-count:{userId}
auth:refresh-blacklist:{tokenId}

credit:application:draft:{userId}
credit:limit-cache:{userId}

cart:items:{userId}    # 장바구니 원본이 아니라 catalog.cart_items 조회 결과 캐시
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

## 3. 장바구니와 주문 처리 원칙

장바구니와 주문은 서로 다른 서비스에서 처리하며, 주문 생성 시 반드시 재검증한다.

### 3.1 장바구니 (service-catalog / AWS EKS)

아래 URI는 도메인 흐름 이해를 위한 예시이며, 상세 요청/응답 필드는 API 명세서를 기준으로 한다.

```
장바구니 담기     POST   /api/v1/cart/items
수량 변경         PATCH  /api/v1/cart/items/{cartItemPublicId}
삭제             DELETE /api/v1/cart/items/{cartItemPublicId}
장바구니 조회     GET    /api/v1/cart/items
```

장바구니는 service-catalog에서 처리하며, 원본은 `catalog.cart_items` 테이블에 저장한다. AWS ElastiCache Redis는 장바구니 조회 캐시로만 사용한다. `cart_items` 테이블을 사용하므로 장바구니 항목 수정/삭제는 `cartItemPublicId` 기준으로 처리한다.

### 3.2 주문 생성 (service-core / On-Prem K8s)

아래 URI는 도메인 흐름 이해를 위한 예시이며, 상세 요청/응답 필드는 API 명세서를 기준으로 한다.

```
주문 생성         POST   /api/v1/orders
주문 상세 조회    GET    /api/v1/orders/{publicId}
주문 취소         PATCH  /api/v1/orders/{publicId}/cancel
```

### 3.3 주문 생성 시 재검증 순서

주문 생성 요청이 들어오면 다음 순서로 반드시 재검증한다.

```
1. 장바구니 항목 조회 (service-catalog API 호출, 원본은 catalog.cart_items)
2. 상품 판매 상태 확인 (ON_SALE 여부)
3. 상품 가격 확인 (장바구니의 가격과 현재 가격 비교)
4. 재고 확인 (stock_quantity >= 주문 수량)
5. 사용자 한도 확인 (credit_limits.total_limit - used_amount >= 주문 금액)
6. 주문 생성 (orders, order_items)
7. 한도 사용 내역 기록 (credit_usage_ledger)
```

AWS 장바구니 데이터는 주문 확정 데이터가 아니다. On-Prem 주문 생성 시점의 검증 결과가 최종 기준이다.
