package com.kkpp.common.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

    @Test
    void invalidRequestHasCorrectStatusAndMessage() {
        assertThat(ErrorCode.INVALID_REQUEST.getStatus()).isEqualTo(400);
        assertThat(ErrorCode.INVALID_REQUEST.getMessage()).isEqualTo("잘못된 요청입니다.");
    }

    @Test
    void unauthorizedHasCorrectStatusAndMessage() {
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(401);
        assertThat(ErrorCode.UNAUTHORIZED.getMessage()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    void forbiddenHasCorrectStatusAndMessage() {
        assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(403);
        assertThat(ErrorCode.FORBIDDEN.getMessage()).isEqualTo("권한이 없습니다.");
    }

    @Test
    void resourceNotFoundHasCorrectStatusAndMessage() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getStatus()).isEqualTo(404);
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    void internalServerErrorHasCorrectStatusAndMessage() {
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).isEqualTo(500);
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()).isEqualTo("서버 오류가 발생했습니다.");
    }

    @Test
    void containsExactlyFiveValues() {
        assertThat(ErrorCode.values()).hasSize(5);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void allErrorCodesHaveNonNullMessage(ErrorCode errorCode) {
        assertThat(errorCode.getMessage()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void allErrorCodesHavePositiveStatus(ErrorCode errorCode) {
        assertThat(errorCode.getStatus()).isPositive();
    }

    @Test
    void nameReturnsEnumConstantName() {
        // name() is used by ErrorResponse.from() - verify it returns the enum constant name
        assertThat(ErrorCode.INVALID_REQUEST.name()).isEqualTo("INVALID_REQUEST");
        assertThat(ErrorCode.UNAUTHORIZED.name()).isEqualTo("UNAUTHORIZED");
        assertThat(ErrorCode.FORBIDDEN.name()).isEqualTo("FORBIDDEN");
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.name()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.name()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void valueOfReturnsCorrectEnumConstant() {
        assertThat(ErrorCode.valueOf("INVALID_REQUEST")).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(ErrorCode.valueOf("RESOURCE_NOT_FOUND")).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void errorCodeDoesNotExposeCodeField() {
        // After the PR change, ErrorCode no longer has a separate `code` field.
        // The enum name() is now used as the code. Verify no getCode() method exists.
        boolean hasGetCodeMethod;
        try {
            ErrorCode.class.getMethod("getCode");
            hasGetCodeMethod = true;
        } catch (NoSuchMethodException e) {
            hasGetCodeMethod = false;
        }
        assertThat(hasGetCodeMethod).as("ErrorCode should not have a getCode() method after PR change").isFalse();
    }
}