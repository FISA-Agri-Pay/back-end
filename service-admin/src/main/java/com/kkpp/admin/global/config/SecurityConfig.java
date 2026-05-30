package com.kkpp.admin.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.core.response.ErrorResponse;
import com.kkpp.common.security.jwt.JwtAuthenticationFilter;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// local 프로필에서만 적용되는 관리자 서비스 보안 설정
@Configuration
@Profile("local")
public class SecurityConfig {

    // 로컬 개발 환경에서 상품 관리 API를 인증 없이 테스트하기 위한 필터 체인
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            @Value("${admin.upload.product-image-public-url-prefix:/uploads/products}") String publicUrlPrefix
    ) throws Exception {
        String productImageResourcePattern = normalizePublicUrlPrefix(publicUrlPrefix) + "/**";

        return http
                // REST API 테스트 중 CSRF 토큰 없이 POST/PATCH/DELETE를 호출할 수 있게 함
                .csrf(AbstractHttpConfigurer::disable)
                // 브라우저 기반 관리자 페이지에서 오는 CORS 요청을 허용함
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // POST 전 브라우저가 보내는 OPTIONS 프리플라이트 요청을 허용함
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 로컬 테스트 대상 엔드포인트와 API 문서는 인증 없이 접근 가능함
                        .requestMatchers(
                                "/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                productImageResourcePattern,
                                "/products/**",
                                "/api/v1/admin/credit-reviews/**",
                                "/api/v1/admin/bnpl/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(@Value("${jwt.secret}") String secret) {
        return new JwtTokenProvider(secret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            @Value("${jwt.secret}") String jwtSecret,
            AuthenticationEntryPoint authenticationEntryPoint
    ) {
        return new JwtAuthenticationFilter(jwtSecret, authenticationEntryPoint);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.fail(ErrorResponse.from(ErrorCode.UNAUTHORIZED))
            );
        };
    }

    // 업로드 이미지 공개 URL prefix를 Spring Security matcher에 사용할 경로 패턴으로 정규화하는 함수이다.
    private String normalizePublicUrlPrefix(String publicUrlPrefix) {
        String normalizedPrefix = publicUrlPrefix.startsWith("/") ? publicUrlPrefix : "/" + publicUrlPrefix;
        return normalizedPrefix.endsWith("/")
                ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1)
                : normalizedPrefix;
    }

    // 브라우저의 POST/PATCH/DELETE 프리플라이트 요청을 허용하는 CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 로컬 프론트엔드 포트가 바뀌어도 테스트할 수 있도록 모든 Origin 패턴을 허용함
        configuration.setAllowedOriginPatterns(List.of("*"));
        // 관리자 상품 관리에서 사용할 HTTP 메서드 목록
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        // Authorization, Content-Type 등 테스트에 필요한 요청 헤더를 모두 허용함
        configuration.setAllowedHeaders(List.of("*"));
        // 쿠키 기반 인증을 사용하지 않는 테스트 설정이므로 credential은 비활성화함
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
