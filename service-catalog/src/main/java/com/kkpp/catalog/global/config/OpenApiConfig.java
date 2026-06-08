package com.kkpp.catalog.global.config;

import com.kkpp.common.security.annotation.AuthUser;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "KKPP Catalog API",
                version = "v1",
                description = "농업인 한도 기반 외상 구매 서비스 - 카탈로그 API"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 후 발급된 Access Token을 입력하세요. (Bearer 접두사 불필요)"
)
public class OpenApiConfig {

    @PostConstruct
    public void init() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser.class);
    }
}
