package com.kkpp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPaymentPinRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "결제 PIN은 숫자 6자리여야 합니다.") String pin
) {
}