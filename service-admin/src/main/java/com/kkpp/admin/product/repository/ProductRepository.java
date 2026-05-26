package com.kkpp.admin.product.repository;

import com.kkpp.admin.product.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// 상품 조회, 저장, 동적 조건 검색을 담당하는 JPA Repository임
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // 외부 노출용 UUID로 상품을 조회함
    Optional<Product> findByPublicId(UUID publicId);
}
