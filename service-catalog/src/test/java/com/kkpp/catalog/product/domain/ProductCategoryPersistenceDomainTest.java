package com.kkpp.catalog.product.domain;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.set;
import static org.assertj.core.api.Assertions.assertThat;

import com.kkpp.catalog.category.domain.Category;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductCategoryPersistenceDomainTest {

    @Test
    void productPrePersistAssignsPublicIdOnlyWhenMissing() throws Exception {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        Product product = product(1L, null, category, "ON_SALE", 10, new BigDecimal("12000"));
        Method method = Product.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);

        method.invoke(product);

        assertThat(product.getPublicId()).isNotNull();

        UUID existingPublicId = product.getPublicId();

        method.invoke(product);

        assertThat(product.getPublicId()).isEqualTo(existingPublicId);
    }

    @Test
    void categoryPrePersistAssignsPublicIdOnlyWhenMissing() throws Exception {
        Category category = category(1L, null, "비료", "ACTIVE");
        Method method = Category.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);

        method.invoke(category);

        assertThat(category.getPublicId()).isNotNull();

        UUID existingPublicId = category.getPublicId();
        set(category, "publicId", existingPublicId);

        method.invoke(category);

        assertThat(category.getPublicId()).isEqualTo(existingPublicId);
    }
}
