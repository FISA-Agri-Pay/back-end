package com.kkpp.catalog.product.controller;

import com.kkpp.catalog.product.dto.response.ProductDetailResponse;
import com.kkpp.catalog.product.dto.response.ProductSummaryResponse;
import com.kkpp.catalog.product.service.ProductService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "농자재 상품 API", description = "농자재 상점의 상품 목록과 상품 상세 정보를 조회합니다.")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "상품 목록 조회", description = "카테고리와 검색어 조건으로 농자재 상품 목록을 조회합니다.")
    public ApiResponse<List<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(productService.getProducts(categoryId, keyword));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "상품 상세 조회", description = "상품 publicId로 상품 상세 정보, 가격, 재고, 판매 상태를 조회합니다.")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable UUID productId) {
        return ApiResponse.success(productService.getProduct(productId));
    }
}
