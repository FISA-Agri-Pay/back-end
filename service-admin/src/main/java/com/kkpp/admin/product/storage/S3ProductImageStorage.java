package com.kkpp.admin.product.storage;

import com.kkpp.admin.product.dto.ProductImageUploadResponse;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Profile("prod")
public class S3ProductImageStorage implements ProductImageStorage {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String KEY_PREFIX = "products/";

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrlPrefix;

    public S3ProductImageStorage(
            S3Client s3Client,
            @Value("${cloud.aws.s3.bucket}") String bucketName,
            @Value("${cloud.aws.s3.public-url-prefix}") String publicUrlPrefix
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicUrlPrefix = normalizePublicUrlPrefix(publicUrlPrefix);
    }

    @Override
    public ProductImageUploadResponse upload(MultipartFile file) {
        validate(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        String objectKey = KEY_PREFIX + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 이미지 저장에 실패했습니다.");
        }

        return new ProductImageUploadResponse(
                publicUrlPrefix + "/" + objectKey,
                originalFilename,
                file.getSize()
        );
    }

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

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "확장자가 있는 이미지 파일을 업로드해 주세요.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizePublicUrlPrefix(String publicUrlPrefix) {
        String trimmed = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("/")) {
            return "/" + trimmed;
        }
        return trimmed;
    }
}