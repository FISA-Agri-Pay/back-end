package com.kkpp.admin.product.dto;

// 상품 이미지 업로드 API가 프론트에 반환하는 응답 DTO
// 현재 로컬 개발 환경에서는 /uploads/products/... URL을 반환하고,
// 추후 AWS S3 연동 시에는 S3 또는 CloudFront URL을 같은 필드로 반환하면 된다.
public record ProductImageUploadResponse(
        String imageUrl,
        String originalFilename,
        long size
) {
}
