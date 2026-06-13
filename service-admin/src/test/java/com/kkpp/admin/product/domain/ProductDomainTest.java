package com.kkpp.admin.product.domain;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductDomainTest {

    @Test
    void categoryCreateDefaultsToActiveAndValidatesRequiredValues() {
        Category category = Category.create("비료");

        assertThat(category.getPublicId()).isNotNull();
        assertThat(category.isActive()).isTrue();

        assertThatThrownBy(() -> Category.create(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.create("비료", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productCreateAppliesDefaultStatusAndValidatesCategory() {
        Category activeCategory = category(1L, "비료", CategoryStatus.ACTIVE);

        Product product = Product.create(
                activeCategory,
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                null,
                null
        );

        assertThat(product.getPublicId()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);

        Category inactiveCategory = category(2L, "중지 카테고리", CategoryStatus.INACTIVE);
        assertThatThrownBy(() -> Product.create(null, "상품", null, BigDecimal.ONE, 1, "개", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create(inactiveCategory, "상품", null, BigDecimal.ONE, 1, "개", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productCreateRejectsMissingRequiredValues() {
        Category category = category(1L, "비료", CategoryStatus.ACTIVE);

        assertThatThrownBy(() -> Product.create(category, null, null, BigDecimal.ONE, 1, "개", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create(category, "상품", null, null, 1, "개", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create(category, "상품", null, BigDecimal.ONE, null, "개", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create(category, "상품", null, BigDecimal.ONE, 1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productUpdateChangesOnlyProvidedFieldsAndStopSellingHidesProduct() {
        Category category = category(1L, "비료", CategoryStatus.ACTIVE);
        Product product = Product.create(
                category,
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                "old.png",
                ProductStatus.ON_SALE
        );

        product.update(null, "수정 비료", null, new BigDecimal("15000"), null, null, "new.png", ProductStatus.SOLD_OUT);

        assertThat(product.getName()).isEqualTo("수정 비료");
        assertThat(product.getDescription()).isEqualTo("설명");
        assertThat(product.getPrice()).isEqualByComparingTo("15000");
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getImageUrl()).isEqualTo("new.png");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);

        product.stopSelling();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void productUpdateCanReplaceEveryOptionalField() {
        Category originalCategory = category(1L, "비료", CategoryStatus.ACTIVE);
        Category changedCategory = category(2L, "종자", CategoryStatus.ACTIVE);
        Product product = Product.create(
                originalCategory,
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                "old.png",
                ProductStatus.ON_SALE
        );

        product.update(
                changedCategory,
                "유기질 종자",
                "수정 설명",
                new BigDecimal("22000"),
                3,
                "박스",
                "changed.png",
                ProductStatus.HIDDEN
        );

        assertThat(product.getCategory()).isEqualTo(changedCategory);
        assertThat(product.getName()).isEqualTo("유기질 종자");
        assertThat(product.getDescription()).isEqualTo("수정 설명");
        assertThat(product.getPrice()).isEqualByComparingTo("22000");
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(product.getUnit()).isEqualTo("박스");
        assertThat(product.getImageUrl()).isEqualTo("changed.png");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void productUpdateWithNoValuesKeepsCurrentState() {
        Category category = category(1L, "비료", CategoryStatus.ACTIVE);
        Product product = Product.create(
                category,
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                "old.png",
                ProductStatus.ON_SALE
        );

        product.update(null, null, null, null, null, null, null, null);

        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getName()).isEqualTo("유기질 비료");
        assertThat(product.getDescription()).isEqualTo("설명");
        assertThat(product.getPrice()).isEqualByComparingTo("12000");
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getUnit()).isEqualTo("포");
        assertThat(product.getImageUrl()).isEqualTo("old.png");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void prePersistAssignsPublicIdOnlyWhenMissing() throws Exception {
        Product product = instantiateProduct();

        invokePrePersist(product);

        assertThat(product.getPublicId()).isNotNull();

        var existingPublicId = product.getPublicId();

        invokePrePersist(product);

        assertThat(product.getPublicId()).isEqualTo(existingPublicId);
    }

    @Test
    void categoryPrePersistAssignsPublicIdOnlyWhenMissing() throws Exception {
        Constructor<Category> constructor = Category.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Category category = constructor.newInstance();
        Method method = Category.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);

        method.invoke(category);

        assertThat(category.getPublicId()).isNotNull();

        var existingPublicId = category.getPublicId();

        method.invoke(category);

        assertThat(category.getPublicId()).isEqualTo(existingPublicId);
    }

    private Product instantiateProduct() throws Exception {
        Constructor<Product> constructor = Product.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Product product = constructor.newInstance();
        Field publicId = Product.class.getDeclaredField("publicId");
        publicId.setAccessible(true);
        publicId.set(product, null);
        return product;
    }

    private void invokePrePersist(Product product) throws Exception {
        Method method = Product.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);
        method.invoke(product);
    }
}
