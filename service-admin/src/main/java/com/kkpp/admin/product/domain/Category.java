package com.kkpp.admin.product.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categories", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// public.categories 테이블과 매핑되는 상품 카테고리 엔티티임
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryStatus status;

    // 상품 등록/수정에 사용할 수 있는 활성 카테고리인지 확인함
    public boolean isActive() {
        return status == CategoryStatus.ACTIVE;
    }
}
