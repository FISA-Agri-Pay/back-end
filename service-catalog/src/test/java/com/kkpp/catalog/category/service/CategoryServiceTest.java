package com.kkpp.catalog.category.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.category.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void getCategoriesReturnsActiveCategories() {
        when(categoryRepository.findAllByStatusOrderById("ACTIVE"))
                .thenReturn(List.of(category(1L, UUID.randomUUID(), "비료", "ACTIVE")));

        var response = categoryService.getCategories();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().name()).isEqualTo("비료");
        verify(categoryRepository).findAllByStatusOrderById("ACTIVE");
    }
}
