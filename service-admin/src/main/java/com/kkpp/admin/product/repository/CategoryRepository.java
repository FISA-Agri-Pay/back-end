package com.kkpp.admin.product.repository;

import com.kkpp.admin.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

// 카테고리 조회를 담당하는 JPA Repository임
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
