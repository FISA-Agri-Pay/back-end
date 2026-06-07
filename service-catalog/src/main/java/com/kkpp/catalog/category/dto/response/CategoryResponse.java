package com.kkpp.catalog.category.dto.response;

import com.kkpp.catalog.category.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 조회 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,
        @Schema(description = "카테고리명", example = "비료")
        String name
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
