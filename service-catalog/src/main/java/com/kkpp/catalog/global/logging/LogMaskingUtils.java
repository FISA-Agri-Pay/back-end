package com.kkpp.catalog.global.logging;

import java.util.Collection;
import java.util.UUID;

public final class LogMaskingUtils {

    private LogMaskingUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "****";
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 7) {
            return "****";
        }
        if (digits.length() < 11) {
            return digits.substring(0, 2) + "****" + digits.substring(digits.length() - 2);
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

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

    public static String summarizeCollection(Collection<?> values) {
        if (values == null) {
            return "null";
        }
        return "size=" + values.size();
    }
}
