package com.kkpp.common.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kkpp.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successWithDataReturnsSuccessTrueAndData() {
        String data = "응답 데이터";
        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
    }

    @Test
    void successWithNullDataReturnsSuccessTrueAndNullData() {
        ApiResponse<String> response = ApiResponse.success(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void successVoidReturnsSuccessTrueAndNullDataAndNullError() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void failWithErrorResponseReturnsSuccessFalseAndError() {
        ErrorResponse error = ErrorResponse.from(ErrorCode.INVALID_REQUEST);
        ApiResponse<Void> response = ApiResponse.fail(error);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isEqualTo(error);
    }

    @Test
    void failWithNotFoundErrorResponseContainsCorrectCodeAndMessage() {
        ErrorResponse error = ErrorResponse.from(ErrorCode.RESOURCE_NOT_FOUND);
        ApiResponse<Void> response = ApiResponse.fail(error);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getError().message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    void successWithObjectDataPreservesType() {
        record SampleData(Long id, String name) {}
        SampleData data = new SampleData(1L, "테스트");

        ApiResponse<SampleData> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getData().name()).isEqualTo("테스트");
        assertThat(response.getError()).isNull();
    }

    @Test
    void failDoesNotContainStatusOrErrorCodeAsLegacyFields() {
        // After PR change, ApiResponse no longer has a String status field or errorCode field.
        // Verify no getStatus() returning String, and no getErrorCode() method exists.
        boolean hasStringGetStatus;
        boolean hasGetErrorCode;
        try {
            java.lang.reflect.Method m = ApiResponse.class.getMethod("getStatus");
            hasStringGetStatus = m.getReturnType() == String.class;
        } catch (NoSuchMethodException e) {
            hasStringGetStatus = false;
        }
        try {
            ApiResponse.class.getMethod("getErrorCode");
            hasGetErrorCode = true;
        } catch (NoSuchMethodException e) {
            hasGetErrorCode = false;
        }
        assertThat(hasStringGetStatus).as("ApiResponse should not have getStatus() returning String after PR change").isFalse();
        assertThat(hasGetErrorCode).as("ApiResponse should not have getErrorCode() after PR change").isFalse();
    }

    @Test
    void successWithInternalServerErrorFail() {
        ErrorResponse error = ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR);
        ApiResponse<Void> response = ApiResponse.fail(error);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getError().message()).isEqualTo("서버 오류가 발생했습니다.");
    }

    @Test
    void isSuccessReturnsBooleanNotString() throws NoSuchMethodException {
        // Verify the field is boolean (primitive) not String "SUCCESS"/"ERROR"
        java.lang.reflect.Method isSuccessMethod = ApiResponse.class.getMethod("isSuccess");
        assertThat(isSuccessMethod.getReturnType()).isEqualTo(boolean.class);
    }
}