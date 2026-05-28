package com.kkpp.admin.product.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// public.products 테이블과 매핑되는 상품 엔티티임
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    // 상품 등록 시 필요한 기본값을 채워 엔티티를 생성함
    public static Product create(
            Category category,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            String unit,
            String imageUrl,
            ProductStatus status
    ) {
        if (category == null) {
            throw new IllegalArgumentException("Category must not be null");
        }
        if (!category.isActive()) {
            throw new IllegalArgumentException("Category must be ACTIVE to create product");
        }
        if (name == null) {
            throw new IllegalArgumentException("Product name must not be null");
        }
        if (price == null) {
            throw new IllegalArgumentException("Product price must not be null");
        }
        if (stockQuantity == null) {
            throw new IllegalArgumentException("Product stockQuantity must not be null");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Product unit must not be null");
        }

        Product product = new Product();
        product.publicId = UUID.randomUUID();
        product.category = category;
        product.name = name;
        product.description = description;
        product.price = price;
        product.stockQuantity = stockQuantity;
        product.unit = unit;
        product.imageUrl = imageUrl;
        product.status = status == null ? ProductStatus.ON_SALE : status;
        return product;
    }

    // PATCH 요청처럼 전달된 값만 선택적으로 변경함
    public void update(
            Category category,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            String unit,
            String imageUrl,
            ProductStatus status
    ) {
        if (category != null) {
            this.category = category;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (stockQuantity != null) {
            this.stockQuantity = stockQuantity;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (status != null) {
            this.status = status;
        }
    }

    // 관리자 화면에서 판매 중지 처리하는 상태 변경임
    public void stopSelling() {
        this.status = ProductStatus.HIDDEN;
    }
}
