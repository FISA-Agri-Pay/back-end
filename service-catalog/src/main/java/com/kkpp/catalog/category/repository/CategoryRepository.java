package com.kkpp.catalog.category.repository;

import com.kkpp.catalog.category.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByStatusOrderById(String status);
}
