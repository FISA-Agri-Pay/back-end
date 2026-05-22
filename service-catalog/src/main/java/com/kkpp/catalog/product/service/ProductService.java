package com.kkpp.catalog.product.service;

import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.dto.response.ProductDetailResponse;
import com.kkpp.catalog.product.dto.response.ProductSummaryResponse;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductSummaryResponse> getProducts(Long categoryId, String keyword) {
        return productRepository.search(categoryId, normalize(keyword)).stream()
                .map(ProductSummaryResponse::from)
                .toList();
    }

    public ProductDetailResponse getProduct(UUID productId) {
        Product product = productRepository.findByPublicIdWithCategory(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
        return ProductDetailResponse.from(product);
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
