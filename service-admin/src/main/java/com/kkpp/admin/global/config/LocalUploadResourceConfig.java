package com.kkpp.admin.global.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("local")
// 로컬 업로드 파일을 HTTP URL로 열 수 있게 정적 리소스 경로를 연결하는 설정이다.
// 테스트 방법은 이미지 업로드 API 응답으로 받은 /uploads/products/... URL을 브라우저에서 열어보는 것이다.
// 운영에서 S3/CloudFront를 사용하면 이 설정은 필요 없고, S3가 반환하는 공개 URL 또는 서명 URL을 사용하면 된다.
public class LocalUploadResourceConfig implements WebMvcConfigurer {

    private final Path productImageDirectory;
    private final String publicUrlPrefix;

    public LocalUploadResourceConfig(
            @Value("${admin.upload.product-image-dir:./uploads/products}") String productImageDirectory,
            @Value("${admin.upload.product-image-public-url-prefix:/uploads/products}") String publicUrlPrefix
    ) {
        this.productImageDirectory = Path.of(productImageDirectory).toAbsolutePath().normalize();
        this.publicUrlPrefix = normalizePublicUrlPrefix(publicUrlPrefix);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = productImageDirectory.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }

        registry
                .addResourceHandler(publicUrlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }

    // 업로드 응답 URL prefix와 정적 리소스 핸들러 경로를 같은 형식으로 맞추는 함수이다.
    private String normalizePublicUrlPrefix(String publicUrlPrefix) {
        String normalizedPrefix = publicUrlPrefix.startsWith("/") ? publicUrlPrefix : "/" + publicUrlPrefix;
        return normalizedPrefix.endsWith("/")
                ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1)
                : normalizedPrefix;
    }
}
