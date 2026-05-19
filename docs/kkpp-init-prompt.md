첨부한 3개 문서를 기준으로 Spring Boot 3.3.4, Java 21, Gradle Groovy DSL 기반 백엔드 멀티모듈 프로젝트 초기 세팅을 해줘.

참고 문서:
1. 01-backend-convention.md
2. 02-architecture.md
3. 03-domain-rules.md

목표:
- 실제 비즈니스 기능 구현 전, 프로젝트 초기 골격만 생성한다.
- 문서에 정의된 서비스 구조와 코드 컨벤션을 반드시 따른다.
- 문서에 없는 임의의 서비스, 모듈, API를 추가하지 않는다.

반드시 생성할 모듈:
- common-core
- common-security
- service-catalog
- service-core
- service-batch
- service-admin

서비스 구조:
- service-catalog: AWS EKS 배포 대상, product/category/cart/home 패키지 구조
- service-core: On-Prem K8s 배포 대상, auth/user/credit/bnpl/wallet/order/ledger 패키지 구조
- service-batch: On-Prem K8s CronJob 대상, bss/interest/overdue 패키지 구조
- service-admin: On-Prem K8s 배포 대상, adminauth/dashboard/creditreview/overduemanagement/productmanagement/ordermanagement/audit 패키지 구조

패키지 기준:
- 루트 패키지는 com.kkpp로 한다.
- service-catalog: com.kkpp.catalog
- service-core: com.kkpp.core
- service-batch: com.kkpp.batch
- service-admin: com.kkpp.admin
- common-core: com.kkpp.common.core
- common-security: com.kkpp.common.security

기술 스택:
- Spring Boot 3.3.4
- Java 21
- Gradle Groovy DSL
- PostgreSQL
- Redis
- Kafka
- Spring Batch 5.x
- Spring Security
- JPA
- Validation
- Lombok
- MapStruct
- jjwt
- springdoc-openapi

의존성 버전:
- Lombok: 1.18.32
- MapStruct: 1.5.5.Final
- lombok-mapstruct-binding: 0.2.0
- jjwt: 0.12.6
- springdoc-openapi: 2.5.0
- Spring Dependency Management Plugin: 1.1.6
- PostgreSQL Driver는 Spring Boot BOM 기준 버전을 사용한다.
- Spring Kafka, Spring Data JPA, Spring Security, Spring Batch, Validation, Redis는 Spring Boot 3.3.4 BOM 기준 버전을 사용한다.

AWS 의존성 규칙:
- 이번 초기 세팅에서는 AWS SDK 의존성을 추가하지 않는다.
- 추후 S3, SES, SQS 등 AWS 연동이 필요할 때만 io.awspring.cloud:spring-cloud-aws-* 또는 software.amazon.awssdk:* 계열을 사용한다.
- com.amazonaws:aws-java-sdk-* 계열은 절대 사용하지 않는다.

Gradle 의존성 구성 원칙:
1. 루트 build.gradle에서 공통 버전을 ext 또는 dependencyManagement로 관리한다.
2. 각 모듈 build.gradle에서 필요한 의존성만 선언한다.
3. 불필요하게 모든 서비스에 모든 의존성을 넣지 않는다.

Gradle bootJar/jar 규칙:
- common-core, common-security는 실행 애플리케이션이 아니므로 bootJar를 비활성화하고 jar를 활성화한다.
- service-catalog, service-core, service-batch, service-admin은 실행 애플리케이션이므로 bootJar를 활성화한다.
- service-batch는 K8s CronJob에서 실행되는 Spring Boot 애플리케이션이므로 bootJar를 활성화한다.

Annotation Processor 규칙:
- Lombok annotationProcessor를 MapStruct annotationProcessor보다 반드시 먼저 선언한다.
- Lombok과 MapStruct를 함께 사용하는 모듈에는 lombok-mapstruct-binding:0.2.0을 annotationProcessor로 추가한다.

올바른 순서 예시:
annotationProcessor "org.projectlombok:lombok:${lombokVersion}"
annotationProcessor "org.projectlombok:lombok-mapstruct-binding:0.2.0"
annotationProcessor "org.mapstruct:mapstruct-processor:${mapstructVersion}"

jjwt 의존성 규칙:
- jjwt-api는 implementation으로 선언한다.
- jjwt-impl, jjwt-jackson은 runtimeOnly로 선언한다.

모듈 간 의존관계:
- common-core는 다른 프로젝트 모듈에 의존하지 않는다.
- common-security는 common-core에 의존한다.
- service-catalog는 common-core, common-security에 의존한다.
- service-core는 common-core, common-security에 의존한다.
- service-admin은 common-core, common-security에 의존한다.
- service-batch는 common-core에만 의존한다.
- service-batch는 common-security에 의존하지 않는다.

모듈별 주요 의존성:
1. common-core
   - spring-boot-starter
   - spring-boot-starter-data-jpa
   - spring-boot-starter-validation
   - lombok

2. common-security
   - common-core
   - spring-boot-starter-security
   - jjwt-api
   - jjwt-impl (runtimeOnly)
   - jjwt-jackson (runtimeOnly)
   - lombok

3. service-catalog
   - common-core
   - common-security
   - spring-boot-starter-web
   - spring-boot-starter-data-jpa
   - spring-boot-starter-data-redis
   - spring-boot-starter-validation
   - postgresql
   - mapstruct
   - lombok
   - springdoc-openapi

4. service-core
   - common-core
   - common-security
   - spring-boot-starter-web
   - spring-boot-starter-data-jpa
   - spring-boot-starter-data-redis
   - spring-boot-starter-validation
   - spring-kafka
   - postgresql
   - mapstruct
   - lombok
   - springdoc-openapi

5. service-admin
   - common-core
   - common-security
   - spring-boot-starter-web
   - spring-boot-starter-data-jpa
   - spring-boot-starter-validation
   - spring-kafka
   - postgresql
   - mapstruct
   - lombok
   - springdoc-openapi

6. service-batch
   - common-core
   - spring-boot-starter-batch
   - spring-boot-starter-data-jpa
   - spring-kafka
   - postgresql
   - lombok
   - mapstruct

application.yml profile 기준:
- profile은 local, dev, prod 세 개로 분리한다.
- application.yml에는 공통 설정만 둔다.
- application-local.yml은 로컬 개발용이다.
- application-dev.yml은 개발 서버 또는 테스트 배포용이다.
- application-prod.yml은 운영 또는 최종 배포용이다.
- 민감정보는 yml에 직접 쓰지 않고 환경변수로 주입한다.
- DB, Redis, Kafka, JWT secret은 환경변수 기반 placeholder를 사용한다.

예시:
spring:
  profiles:
    active: local

환경변수 예시:
- DB_URL
- DB_USERNAME
- DB_PASSWORD
- REDIS_HOST
- REDIS_PORT
- KAFKA_BOOTSTRAP_SERVERS
- JWT_SECRET

이번 작업에서 구현할 것:
1. settings.gradle
2. root build.gradle
3. 각 모듈 build.gradle
4. 각 service 모듈의 MainApplication 클래스
5. common-core의 ApiResponse, ErrorResponse, BusinessException, ErrorCode 기본 구조
6. common-core의 BaseEntity, BaseTimeEntity
7. common-core의 event 패키지와 샘플 이벤트 Record
8. common-security의 @AuthUser, AuthUserInfo, JwtAuthenticationFilter 골격
9. 각 서비스의 기본 패키지 구조
10. 각 서비스의 샘플 HealthController
11. application.yml, application-local.yml, application-dev.yml, application-prod.yml 기본 구조
12. service-batch의 Spring Batch 5.x 설정 골격과 샘플 Job 구조

이번 작업에서 구현하지 말 것:
- 회원가입 실제 로직
- 로그인 실제 로직
- 주문 생성 실제 로직
- 장바구니 실제 로직
- 한도 심사 실제 로직
- Redis 실제 비즈니스 로직
- Kafka Producer/Consumer 실제 비즈니스 로직
- 전체 Entity 구현
- 전체 API 구현
- Kubernetes manifest 작성
- Dockerfile 작성
- 실제 상품/주문/한도 도메인 로직 구현

Spring Batch 주의사항:
- Spring Batch 5.x 기준으로 작성한다.
- JobBuilderFactory, StepBuilderFactory를 사용하지 않는다.
- JobBuilder, StepBuilder를 JobRepository와 함께 직접 사용한다.
- PlatformTransactionManager를 명시적으로 주입하는 구조로 작성한다.

올바른 예시:
@Bean
public Job sampleJob(JobRepository jobRepository, Step sampleStep) {
    return new JobBuilder("sampleJob", jobRepository)
            .start(sampleStep)
            .build();
}

금지:
@Autowired JobBuilderFactory jobBuilderFactory;
@Autowired StepBuilderFactory stepBuilderFactory;

코드 작성 규칙:
- Controller는 HTTP 요청/응답만 처리하는 얇은 구조로 둔다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- 외부 응답에는 내부 Long id를 노출하지 않고 publicId 사용 원칙을 따른다.
- Request DTO와 Response DTO를 분리한다.
- Entity → Response DTO 변환은 MapStruct를 사용한다.
- 예외는 BusinessException 기반으로 처리한다.
- 응답은 ApiResponse<T>로 감싼다.
- 아직 실제 도메인 Entity는 만들지 말고, 공통 기반 클래스와 샘플 구조까지만 만든다.

결과 형식:
1. 전체 파일 트리
2. settings.gradle
3. root build.gradle
4. 각 모듈 build.gradle
5. 주요 공통 클래스 코드
6. 각 서비스 MainApplication 코드
7. 각 서비스 HealthController 코드
8. application.yml profile 구조
9. 빌드 실행 방법

마지막으로, 생성 후 다음 명령이 성공하도록 구성한다.

./gradlew clean build
