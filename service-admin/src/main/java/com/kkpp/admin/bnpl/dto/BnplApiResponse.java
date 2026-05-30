package com.kkpp.admin.bnpl.dto;

public record BnplApiResponse<T>(
        boolean success,
        T data
) {

    public static <T> BnplApiResponse<T> success(T data) {
        return new BnplApiResponse<>(true, data);
    }
}
