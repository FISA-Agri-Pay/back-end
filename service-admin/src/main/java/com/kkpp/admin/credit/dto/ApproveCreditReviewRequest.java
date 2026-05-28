package com.kkpp.admin.credit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// 관리자가 한도 신청을 최종 승인할 때 보내는 요청 DTO
// approvedAmount는 필수이며, 이율과 상환/만료일은 생략 시 서비스의 임시 기본 정책값을 사용한다.
public record ApproveCreditReviewRequest(
        @NotNull(message = "reviewedBy is required")
        Long reviewedBy,

        @NotNull(message = "approvedAmount is required")
        @DecimalMin(value = "0.01", message = "approvedAmount must be positive")
        @Digits(integer = 13, fraction = 2, message = "approvedAmount must fit numeric(15,2)")
        BigDecimal approvedAmount,

        @DecimalMin(value = "0.0000", message = "interestRate must not be negative")
        @Digits(integer = 2, fraction = 4, message = "interestRate must fit numeric(6,4)")
        BigDecimal interestRate,

        LocalDate principalDueDate,
        LocalDate expiresAt
) {
}
