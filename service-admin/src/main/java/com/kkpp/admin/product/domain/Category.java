package com.kkpp.admin.product.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categories", schema = "catalog")
// JPA 전용 기본 생성자임. 엔티티는 repository를 통해서만 관리되어야 함
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// catalog.categories 테이블과 매핑되는 상품 카테고리 엔티티임
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryStatus status;

    // 카테고리 엔티티를 생성함. 기본 상태는 ACTIVE임
    public static Category create(String name, CategoryStatus status) {
        if (name == null) {
            throw new IllegalArgumentException("Category name must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Category status must not be null");
        }

        Category category = new Category();
        category.publicId = UUID.randomUUID();
        category.name = name;
        category.status = status;
        return category;
    }

    // 카테고리 엔티티를 ACTIVE 상태로 생성함
    public static Category create(String name) {
        return create(name, CategoryStatus.ACTIVE);
    }

    // 상품 등록/수정에 사용할 수 있는 활성 카테고리인지 확인함
    public boolean isActive() {
        return status == CategoryStatus.ACTIVE;
    }

    @PrePersist
    private void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
