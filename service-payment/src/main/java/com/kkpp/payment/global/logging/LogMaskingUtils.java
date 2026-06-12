package com.kkpp.payment.global.logging;

import java.util.Collection;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogMaskingUtils {

    public static String maskIdentifier(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.length() <= 8) {
            return "****";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    public static String summarizeCollection(Collection<?> values) {
        if (values == null) {
            return null;
        }
        return "count=" + values.size();
    }

    public static String maskUuid(UUID value) {
        return value == null ? null : maskIdentifier(value);
    }
}
