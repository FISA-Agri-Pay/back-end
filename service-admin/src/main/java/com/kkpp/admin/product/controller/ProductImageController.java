package com.kkpp.admin.product.controller;

import com.kkpp.admin.product.dto.ProductImageUploadResponse;
import com.kkpp.admin.product.storage.ProductImageStorage;
import com.kkpp.common.core.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products/images")
@RequiredArgsConstructor
// 관리자 상품 대표 이미지 업로드 API이다.
// 프론트는 상품 등록/수정 전에 이 API로 파일을 먼저 업로드하고, 응답의 imageUrl을 상품 저장 API에 포함한다.
// 로컬 개발 환경에서는 파일이 ./uploads/products에 저장되고 /uploads/products/... URL로 서빙된다.
// AWS S3 연동 시에도 이 컨트롤러의 요청/응답 형식은 유지하고 ProductImageStorage 구현체만 S3용으로 바꾸면 된다.
public class ProductImageController {

    private final ProductImageStorage productImageStorage;

    // multipart/form-data의 file 파트를 받아 저장소에 위임하고 접근 가능한 이미지 URL을 반환한다.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImageUploadResponse> uploadProductImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(productImageStorage.upload(file));
    }
}
