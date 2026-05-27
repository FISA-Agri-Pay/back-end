package com.kkpp.admin.product.storage;

import com.kkpp.admin.product.dto.ProductImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

// 상품 이미지 저장소의 공통 계약이다.
// 현재 local 프로필에서는 서버 로컬 디렉터리에 파일을 저장하는 구현체를 사용한다.
// 추후 AWS S3를 사용할 때는 이 인터페이스를 구현하는 S3ProductImageStorage를 추가하면 된다.
// 컨트롤러와 프론트 API 계약은 그대로 두고 저장소 구현만 바꿀 수 있게 하기 위한 구조이다.
public interface ProductImageStorage {

    // 업로드된 파일을 저장하고 상품 등록/수정 DTO에 넣을 imageUrl을 반환한다.
    ProductImageUploadResponse upload(MultipartFile file);
}
