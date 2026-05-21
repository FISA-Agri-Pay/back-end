package com.kkpp.common.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final String status;
    private final T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String errorCode;
    private final String message;

    private ApiResponse(String status, T data, String errorCode, String message) {
        this.status = status;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "요청이 성공적으로 처리되었습니다.");
    }

    public static ApiResponse<Void> success() {
        return success(null, "요청이 성공적으로 처리되었습니다.");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("SUCCESS", data, null, message);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return fail(error.code(), error.message());
    }

    public static ApiResponse<Void> fail(String errorCode, String message) {
        return new ApiResponse<>("ERROR", null, errorCode, message);
    }
}
