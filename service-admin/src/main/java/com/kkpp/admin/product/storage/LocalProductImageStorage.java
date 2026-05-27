package com.kkpp.admin.product.storage;

import com.kkpp.admin.product.dto.ProductImageUploadResponse;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local")
// 로컬 개발 환경에서 상품 이미지를 서버 파일 시스템에 저장하는 저장소 구현체
// 테스트 방법은 관리자 프론트에서 이미지를 선택해 상품을 등록하거나,
// curl -F "file=@sample.jpg" http://localhost:8080/products/images 로 직접 호출하는 것이다.
// 반환된 imageUrl은 /uploads/products/... 형태이며 브라우저에서 바로 열어 정적 서빙을 확인할 수 있다.
// 추후 AWS 연동 시에는 이 클래스 대신 S3 업로드 구현체를 만들고 같은 ProductImageStorage 인터페이스를 구현하면 된다.
public class LocalProductImageStorage implements ProductImageStorage {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final Path uploadDirectory;
    private final String publicUrlPrefix;

    public LocalProductImageStorage(
            @Value("${admin.upload.product-image-dir:./uploads/products}") String uploadDirectory,
            @Value("${admin.upload.product-image-public-url-prefix:/uploads/products}") String publicUrlPrefix
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.publicUrlPrefix = normalizePublicUrlPrefix(publicUrlPrefix);
    }

    @Override
    public ProductImageUploadResponse upload(MultipartFile file) {
        validate(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadDirectory.resolve(storedFilename).normalize();

        try {
            Files.createDirectories(uploadDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 이미지 저장에 실패했습니다.");
        }

        return new ProductImageUploadResponse(
                publicUrlPrefix + "/" + storedFilename,
                originalFilename,
                file.getSize()
        );
    }

    // 업로드 가능한 이미지 파일인지 검증하는 로컬 저장소 공통 규칙이다.
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "업로드할 이미지 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "상품 이미지는 10MB 이하만 업로드할 수 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미지 파일만 업로드할 수 있습니다.");
        }

        String extension = extractExtension(StringUtils.cleanPath(file.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "jpg, jpeg, png, gif, webp 이미지만 업로드할 수 있습니다.");
        }
    }

    // 원본 파일명에서 확장자를 추출하고 소문자로 정규화한다.
    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "확장자가 있는 이미지 파일을 업로드해 주세요.");
        }

        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    // 업로드 응답 URL과 리소스 핸들러 경로가 같은 prefix를 쓰도록 형식을 정규화한다.
    private String normalizePublicUrlPrefix(String publicUrlPrefix) {
        String normalizedPrefix = publicUrlPrefix.startsWith("/") ? publicUrlPrefix : "/" + publicUrlPrefix;
        return normalizedPrefix.endsWith("/")
                ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1)
                : normalizedPrefix;
    }
}
