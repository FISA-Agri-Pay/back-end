package com.kkpp.catalog.category.controller;

import com.kkpp.catalog.category.dto.response.CategoryResponse;
import com.kkpp.catalog.category.service.CategoryService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
@Tag(name = "카테고리 API", description = "농자재 상품 카테고리 목록을 조회합니다.")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "카테고리 목록 조회", description = "활성화된 카테고리 목록을 조회합니다.")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryService.getCategories());
    }
}
