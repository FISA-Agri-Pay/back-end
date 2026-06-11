package com.kkpp.core.global.logging;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LogMaskingUtils {

    private static final int DEFAULT_VISIBLE_TAIL = 6;

    private LogMaskingUtils() {
    }

    /**
     * UUID, 세션 ID, 토큰처럼 추적에는 필요하지만 원문 노출이 부담되는 값을 끝자리만 남깁니다.
     */
    public static String maskIdentifier(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "");
        if (text.isBlank()) {
            return "";
        }
        int visibleTail = Math.min(DEFAULT_VISIBLE_TAIL, text.length());
        return "***" + text.substring(text.length() - visibleTail);
    }

    /**
     * 저장소 key는 디렉터리 구조만 남기고 실제 파일 식별자는 마스킹합니다.
     */
    public static String maskStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return storageKey;
        }
        int lastSlash = storageKey.lastIndexOf('/');
        if (lastSlash < 0) {
            return maskIdentifier(storageKey);
        }
        return storageKey.substring(0, lastSlash + 1) + maskIdentifier(storageKey.substring(lastSlash + 1));
    }

    /**
     * 요청 파일 key처럼 값 자체가 민감하지 않은 목록을 로그 필드 하나로 압축합니다.
     */
    public static String summarizeCollection(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> value == null ? "null" : value.toString())
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 예외 inputValue를 로그에 남길 때 원문 객체 전체가 찍히지 않도록 안전한 설명으로 바꿉니다.
     */
    public static String describeSafe(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            return summarizeCollection(collection);
        }
        return value.getClass().getSimpleName() + "(" + maskIdentifier(value) + ")";
    }
}
