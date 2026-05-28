package com.kkpp.batch.bss.dto;

import java.time.LocalDateTime;

import com.kkpp.batch.bss.domain.PeriodType;

// Writer가 bss_scores에 저장할 월별 BSS 계산 결과다.
public record BssCalculationResult(
        Long userId,
        int repaymentScore,
        int overdueScore,
        int usageScore,
        int monthlyScore,
        int totalScore,
        PeriodType periodType,
        int periodYear,
        int periodMonth,
        LocalDateTime calculatedAt
) {
}
