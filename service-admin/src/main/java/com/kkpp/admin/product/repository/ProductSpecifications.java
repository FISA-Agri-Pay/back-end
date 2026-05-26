package com.kkpp.admin.product.repository;

import com.kkpp.admin.product.domain.Category;
import com.kkpp.admin.product.domain.Product;
import com.kkpp.admin.product.domain.ProductStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

// 상품 목록 조회에서 사용하는 동적 검색 조건 모음임
public final class ProductSpecifications {

    private static final long PRODUCT_NUMBER_OFFSET = 10000L;

    private ProductSpecifications() {
    }

    // 카테고리 id가 전달된 경우 해당 카테고리 상품만 조회함
    public static Specification<Product> categoryIdEquals(Long categoryId) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("category").get("id"), categoryId);
        };
    }

    // 카테고리명이 전달된 경우 해당 카테고리 상품만 조회함
    public static Specification<Product> categoryNameEquals(String categoryName) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(categoryName)) {
                return null;
            }
            Join<Product, Category> category = root.join("category", JoinType.INNER);
            return criteriaBuilder.equal(category.get("name"), categoryName.trim());
        };
    }

    // 상품 상태가 전달된 경우 해당 상태 상품만 조회함
    public static Specification<Product> statusEquals(ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    // 상품명 또는 화면용 상품번호(P10038 형태)에 검색어가 맞는 상품을 조회함
    public static Specification<Product> keywordContains(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }

            String trimmedKeyword = keyword.trim();
            String likeKeyword = "%" + trimmedKeyword.toLowerCase(Locale.ROOT) + "%";
            var namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likeKeyword);

            Long idFromProductNumber = parseProductNumber(trimmedKeyword);
            if (idFromProductNumber == null) {
                return namePredicate;
            }

            return criteriaBuilder.or(namePredicate, criteriaBuilder.equal(root.get("id"), idFromProductNumber));
        };
    }

    // 화면용 상품번호를 내부 id로 되돌려 검색 조건에 사용함
    private static Long parseProductNumber(String keyword) {
        if (!keyword.matches("(?i)^P\\d+$")) {
            return null;
        }

        long number = Long.parseLong(keyword.substring(1));
        long id = number - PRODUCT_NUMBER_OFFSET;
        return id > 0 ? id : null;
    }
}
