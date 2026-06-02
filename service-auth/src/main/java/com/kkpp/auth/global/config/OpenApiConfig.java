package com.kkpp.auth.global.config;

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
                title = "KKPP 인증 API",
                version = "v1",
                description = "회원가입, 로그인, 토큰 발급, 결제 PIN 등록을 제공하는 인증 서비스 API"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 또는 회원가입으로 발급받은 access token을 Bearer 형식으로 입력합니다."
)
public class OpenApiConfig {

    @PostConstruct
    public void init() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser.class);
    }
}
