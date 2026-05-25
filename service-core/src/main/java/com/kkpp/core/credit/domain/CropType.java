package com.kkpp.core.credit.domain;

import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;

import java.util.Arrays;

public enum CropType {

    RICE,
    BEAN,
    PEPPER,
    ONION,
    GARLIC,
    CUSTOM;

    public static CropType from(String value) {
        if (value == null || value.isBlank()) {
            throw new CreditException(CreditErrorCode.CROP_UNSUPPORTED_TYPE, value);
        }
        return Arrays.stream(values())
                .filter(cropType -> cropType.name().equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new CreditException(CreditErrorCode.CROP_UNSUPPORTED_TYPE, value));
    }
}
