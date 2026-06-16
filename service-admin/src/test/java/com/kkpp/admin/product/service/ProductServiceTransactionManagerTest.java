package com.kkpp.admin.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProductService의 트랜잭션 매니저 매칭을 보호하는 회귀 테스트이다.
 *
 * <p>ProductRepository는 catalog 전용 EntityManagerFactory({@code catalogEntityManagerFactory})에 묶여 있으므로
 * 쓰기/조회 트랜잭션은 반드시 {@code catalogTransactionManager}로 실행되어야 커밋이 catalog DB에 반영된다.
 * 메서드 레벨 {@code @Transactional}은 클래스 레벨 설정을 병합하지 않고 완전히 덮어쓰기 때문에,
 * 매니저를 지정하지 않은 맨 {@code @Transactional}을 쓰면 @Primary인 coreTransactionManager로 새어나가
 * "성공 응답 + DB 미반영 + 무오류" 형태로 조용히 실패한다(#119 회귀).
 *
 * <p>이 테스트는 Spring 컨텍스트/실DB 없이 어노테이션 계약만 검증하여 해당 부류의 회귀를 빠르게 차단한다.
 */
class ProductServiceTransactionManagerTest {

    private static final String CATALOG_TRANSACTION_MANAGER = "catalogTransactionManager";

    @Test
    void classLevelTransactionalUsesCatalogTransactionManager() {
        Transactional classLevel = ProductService.class.getAnnotation(Transactional.class);

        assertThat(classLevel)
                .as("ProductService 클래스 레벨 @Transactional이 존재해야 한다")
                .isNotNull();
        assertThat(classLevel.transactionManager())
                .as("클래스 레벨 @Transactional은 catalogTransactionManager를 지정해야 한다")
                .isEqualTo(CATALOG_TRANSACTION_MANAGER);
    }

    @Test
    void everyMethodLevelTransactionalUsesCatalogTransactionManager() {
        Arrays.stream(ProductService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .forEach(this::assertUsesCatalogTransactionManager);
    }

    private void assertUsesCatalogTransactionManager(Method method) {
        String transactionManager = method.getAnnotation(Transactional.class).transactionManager();

        assertThat(transactionManager)
                .as("%s()의 메서드 레벨 @Transactional은 catalogTransactionManager를 명시해야 한다. "
                        + "빈 값이면 @Primary인 coreTransactionManager로 새어나가 catalog DB에 커밋되지 않는다.", method.getName())
                .isEqualTo(CATALOG_TRANSACTION_MANAGER);
    }
}
