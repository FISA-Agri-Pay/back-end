package com.kkpp.auth.global.logging;

import java.util.UUID;

public final class LogMaskingUtils {

    private LogMaskingUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "****";
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "****";
        }

        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    // UUID, 해시, 메시지 ID처럼 식별 가능한 값은 뒤 일부만 남겨 로그 검색성과 보호를 함께 맞춥니다.
    public static String maskIdentifier(Object value) {
        if (value == null) {
            return "null";
        }

        String text = value instanceof UUID uuid ? uuid.toString() : String.valueOf(value);
        if (text.length() <= 6) {
            return "****";
        }

        return "****" + text.substring(text.length() - 6);
    }
}
