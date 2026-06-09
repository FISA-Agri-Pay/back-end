---
name: 테스트/커버리지 이슈
about: 단위 테스트, API 테스트, Jacoco 커버리지 보강 작업을 요청합니다.
title: "test: "
labels: test, coverage
assignees: ""
---

## 테스트 대상
> 테스트를 추가할 모듈, 클래스, API를 적어주세요.

- 모듈:
- 대상 클래스/API:
- 관련 기능:

## 테스트 우선순위
> 실패 시 사용자 피해, 금액/한도 영향, 장애 가능성이 큰 영역부터 우선 테스트합니다.


## 테스트 조건 및 예상 결과
> 아래 표에 테스트 케이스를 작성하고, 테스트 실행 후 `단위 테스트 결과`를 `PASS` 또는 `FAIL`로 갱신합니다.

| 테스트 조건 | 예상 결과 | 단위 테스트 결과 | Fail 동작 | 이슈중요도 |
|-------------|-----------|------------------|-----------|------------|
| 예: `POST /api/v1/auth/login` 정상 전화번호/비밀번호 전달 | `200 OK`, access token 반환 및 refresh token 쿠키 설정 | 미실행 | 로그인 실패 또는 쿠키 누락 | 높음 |
| 예: `CheckoutService.createCheckoutRequest` 재고 부족 상품 포함 | 비즈니스 예외 발생, 결제 요청/이벤트 미생성 | 미실행 | 재고 부족 상품 결제 진행 | 높음 |
| 예: `BssCalculationService.calculate` 미해결 연체 존재 | 연체 점수 0점 반영 | 미실행 | 연체 사용자 점수 과대 산정 | 높음 |

## 작성할 테스트 유형

- [ ] Service 단위 테스트: Repository, Redis, Kafka, S3 등 외부 의존성은 mock 처리
- [ ] Controller 테스트: `@WebMvcTest`, `MockMvc`로 요청/응답 검증
- [ ] Repository 테스트: `@DataJpaTest`로 쿼리 검증
- [ ] 통합 테스트: 꼭 필요한 핵심 시나리오만 검증

## 커버리지 목표

- 목표 커버리지: Instruction Coverage 90% 이상
- 측정 도구: Jacoco
- 리포트 경로:

```text
{module}/build/reports/jacoco/test/html/index.html
{module}/build/reports/jacoco/test/jacocoTestReport.xml
```

## 실행 명령

```powershell
.\gradlew.bat :{module}:test
.\gradlew.bat :{module}:jacocoTestReport
.\gradlew.bat :{module}:jacocoTestCoverageVerification
```

## 메인 로직 변경 금지 체크
> 테스트 커버리지 작업은 기본적으로 운영 코드 변경 없이 진행합니다.

- [ ] `src/test/java` 또는 `src/test/resources`에 테스트만 추가했습니다.
- [ ] 운영 코드(`src/main`)를 커버리지 숫자 확보 목적으로 수정하지 않았습니다.
- [ ] 운영 코드 수정이 필요한 버그를 발견한 경우 별도 bug 이슈로 분리했습니다.
- [ ] 테스트 데이터는 고정 UUID, 고정 날짜, 고정 금액으로 재현 가능하게 작성했습니다.

## 완료 조건

- [ ] 테스트가 로컬에서 통과합니다.
- [ ] 실패/예외/경계값 케이스가 포함되어 있습니다.
- [ ] JaCoCo HTML 리포트를 생성했습니다.
- [ ] 목표 커버리지 달성 여부를 이슈에 기록했습니다.
- [ ] 테스트 산출물 표의 `단위 테스트 결과`를 갱신했습니다.

## 참고 문서

- `docs/kkpp-04-test-coverage-guide.md`
