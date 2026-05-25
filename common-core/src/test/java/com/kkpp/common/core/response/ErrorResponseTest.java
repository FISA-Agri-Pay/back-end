package com.kkpp.common.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kkpp.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorResponseTest {

    @Test
    void fromInvalidRequestUsesEnumName() {
        ErrorResponse response = ErrorResponse.from(ErrorCode.INVALID_REQUEST);

        assertThat(response.code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.message()).isEqualTo("잘못된 요청입니다.");
    }

    @Test
    void fromUnauthorizedUsesEnumName() {
        ErrorResponse response = ErrorResponse.from(ErrorCode.UNAUTHORIZED);

        assertThat(response.code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.message()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    void fromForbiddenUsesEnumName() {
        ErrorResponse response = ErrorResponse.from(ErrorCode.FORBIDDEN);

        assertThat(response.code()).isEqualTo("FORBIDDEN");
        assertThat(response.message()).isEqualTo("권한이 없습니다.");
    }

    @Test
    void fromResourceNotFoundUsesEnumName() {
        ErrorResponse response = ErrorResponse.from(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(response.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    void fromInternalServerErrorUsesEnumName() {
        ErrorResponse response = ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(response.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.message()).isEqualTo("서버 오류가 발생했습니다.");
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void fromAlwaysUsesEnumNameAsCode(ErrorCode errorCode) {
        ErrorResponse response = ErrorResponse.from(errorCode);

        // After PR change: code must be enum name(), not a separate code field
        assertThat(response.code()).isEqualTo(errorCode.name());
        assertThat(response.message()).isEqualTo(errorCode.getMessage());
    }

    @Test
    void directConstructionSetsCodeAndMessage() {
        ErrorResponse response = new ErrorResponse("CUSTOM_CODE", "커스텀 메시지");

        assertThat(response.code()).isEqualTo("CUSTOM_CODE");
        assertThat(response.message()).isEqualTo("커스텀 메시지");
    }

    @Test
    void recordEqualityBasedOnFieldValues() {
        ErrorResponse response1 = new ErrorResponse("INVALID_REQUEST", "잘못된 요청입니다.");
        ErrorResponse response2 = new ErrorResponse("INVALID_REQUEST", "잘못된 요청입니다.");

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void fromProducesCodeMatchingEnumNameNotSeparateField() {
        // Regression test: before PR change, from() used errorCode.getCode() which was a separate field.
        // After PR change, from() uses errorCode.name(). This test verifies the code matches the exact
        // enum constant name, not some other string.
        ErrorResponse response = ErrorResponse.from(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(response.code())
                .isEqualTo("RESOURCE_NOT_FOUND")
                .isNotEqualTo("RESOURCE NOT FOUND")
                .doesNotContain(" ");
    }
}