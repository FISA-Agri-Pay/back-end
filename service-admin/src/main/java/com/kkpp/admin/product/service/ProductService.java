package com.kkpp.admin.product.service;

import static com.kkpp.admin.product.repository.ProductSpecifications.categoryIdEquals;
import static com.kkpp.admin.product.repository.ProductSpecifications.categoryNameEquals;
import static com.kkpp.admin.product.repository.ProductSpecifications.keywordContains;
import static com.kkpp.admin.product.repository.ProductSpecifications.statusEquals;

import com.kkpp.admin.product.domain.Category;
import com.kkpp.admin.product.domain.Product;
import com.kkpp.admin.product.domain.ProductStatus;
import com.kkpp.admin.product.dto.CreateProductRequest;
import com.kkpp.admin.product.dto.ProductPageResponse;
import com.kkpp.admin.product.dto.ProductResponse;
import com.kkpp.admin.product.dto.UpdateProductRequest;
import com.kkpp.admin.product.mapper.ProductMapper;
import com.kkpp.admin.product.repository.CategoryRepository;
import com.kkpp.admin.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 상품 관리 비즈니스 로직과 트랜잭션을 담당하는 서비스임
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    // 상품 목록을 조건별로 검색하고 페이지 응답으로 변환함
    @Transactional(readOnly = true)
    public ProductPageResponse getProducts(
            Long categoryId,
            String categoryName,
            ProductStatus status,
            String keyword,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<Product> specification = Specification
                .where(categoryIdEquals(categoryId))
                .and(categoryNameEquals(categoryName))
                .and(statusEquals(status))
                .and(keywordContains(keyword));

        return productMapper.toPageResponse(productRepository.findAll(specification, pageable));
    }

    // 활성 카테고리인지 확인한 뒤 새 상품을 생성함
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = getActiveCategory(request.categoryId());
        Product product = Product.create(
                category,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.unit(),
                request.imageUrl(),
                request.status()
        );

        return productMapper.toResponse(productRepository.save(product));
    }

    // 상품 publicId로 기존 상품을 찾고 요청에 포함된 값만 갱신함
    @Transactional
    public ProductResponse updateProduct(UUID productPublicId, UpdateProductRequest request) {
        Product product = getProduct(productPublicId);
        Category category = request.categoryId() == null ? null : getActiveCategory(request.categoryId());
        product.update(
                category,
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.unit(),
                request.imageUrl(),
                request.status()
        );

        return productMapper.toResponse(product);
    }

    // 판매 중지 요청은 상품을 삭제하지 않고 HIDDEN 상태로 변경함
    @Transactional
    public void stopSellingProduct(UUID productPublicId) {
        Product product = getProduct(productPublicId);
        product.stopSelling();
    }

    // 삭제 요청은 상품을 실제 DB에서 삭제함
    @Transactional
    public void deleteProduct(UUID productPublicId) {
        Product product = getProduct(productPublicId);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "주문 또는 장바구니에서 참조 중인 상품은 삭제할 수 없습니다. 판매 중지를 사용해 주세요."
            );
        }
    }

    // 상품 publicId로 상품을 조회하고 없으면 404 예외를 던짐
    private Product getProduct(UUID productPublicId) {
        return productRepository.findByPublicId(productPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    // 상품 등록/수정에 사용할 수 있는 활성 카테고리인지 검증함
    private Category getActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "비활성 카테고리는 사용할 수 없습니다.");
        }

        return category;
    }

    // 비정상 페이지 크기를 기본값으로 보정하고 최대 크기를 제한함
    private int normalizePageSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
