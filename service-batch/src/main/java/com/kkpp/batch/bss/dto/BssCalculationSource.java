package com.kkpp.batch.bss.dto;

import java.math.BigDecimal;

// Processor에서 한도별 원장 데이터를 모아 BSS 계산 서비스에 전달하는 입력 모델이다.
public record BssCalculationSource(
        Long userId,
        BigDecimal totalLimit,
        BigDecimal usedAmount,
        BigDecimal interestBilledAmount,
        BigDecimal interestPaidAmount,
        BigDecimal principalBilledAmount,
        BigDecimal principalPaidAmount,
        long overdueCount,
        int maxOverdueDays,
        boolean hasUnresolvedOverdue
) {
}
