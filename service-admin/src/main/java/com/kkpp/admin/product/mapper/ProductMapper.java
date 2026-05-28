package com.kkpp.admin.product.mapper;

import com.kkpp.admin.product.domain.Product;
import com.kkpp.admin.product.dto.ProductPageResponse;
import com.kkpp.admin.product.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
// 상품 엔티티를 관리자 API 응답 DTO로 변환하는 매퍼임
public interface ProductMapper {

    // 카테고리 정보와 화면용 상품번호를 포함한 상품 응답으로 변환함
    @Mapping(target = "productNumber", expression = "java(formatProductNumber(product.getId()))")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);

    // Spring Page 결과를 프론트에서 쓰기 쉬운 페이지 응답으로 변환함
    default ProductPageResponse toPageResponse(Page<Product> page) {
        return new ProductPageResponse(
                page.getContent().stream()
                        .map(this::toResponse)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    // 내부 id를 화면에 표시할 P10038 형식의 상품번호로 변환함
    default String formatProductNumber(Long id) {
        if (id == null) {
            return null;
        }
        return "P" + (10000L + id);
    }
}
