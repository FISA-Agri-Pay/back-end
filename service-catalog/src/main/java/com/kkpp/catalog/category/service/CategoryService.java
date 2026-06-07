package com.kkpp.catalog.category.service;

import com.kkpp.catalog.category.dto.response.CategoryResponse;
import com.kkpp.catalog.category.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByStatusOrderById("ACTIVE").stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
