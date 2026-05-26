package com.kkpp.admin.product.controller;

import com.kkpp.admin.product.domain.ProductStatus;
import com.kkpp.admin.product.dto.CreateProductRequest;
import com.kkpp.admin.product.dto.ProductPageResponse;
import com.kkpp.admin.product.dto.ProductResponse;
import com.kkpp.admin.product.dto.UpdateProductRequest;
import com.kkpp.admin.product.service.ProductService;
import com.kkpp.common.core.response.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
// 관리자 상품 관리 API의 HTTP 요청을 받는 컨트롤러임
public class ProductController {

    private final ProductService productService;

    // 상품 목록을 필터, 검색어, 페이지 조건으로 조회함
    @GetMapping
    public ApiResponse<ProductPageResponse> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(productService.getProducts(categoryId, categoryName, status, keyword, page, size));
    }

    // 신규 상품을 등록하고 생성된 상품 정보를 반환함
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity
                .created(URI.create("/products/" + response.publicId()))
                .body(ApiResponse.success(response));
    }

    // publicId로 상품을 찾아 전달된 필드만 수정함
    @PatchMapping("/{productPublicId}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable UUID productPublicId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ApiResponse.success(productService.updateProduct(productPublicId, request));
    }

    // 상품 판매를 중지하기 위해 상태를 HIDDEN으로 변경함
    @PatchMapping("/{productPublicId}/stop-selling")
    public ApiResponse<Void> stopSellingProduct(@PathVariable UUID productPublicId) {
        productService.stopSellingProduct(productPublicId);
        return ApiResponse.success();
    }

    // 상품을 실제 DB에서 삭제함
    @DeleteMapping("/{productPublicId}")
    public ApiResponse<Void> deleteProduct(@PathVariable UUID productPublicId) {
        productService.deleteProduct(productPublicId);
        return ApiResponse.success();
    }
}
