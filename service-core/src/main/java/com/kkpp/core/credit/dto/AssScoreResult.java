package com.kkpp.core.credit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssScoreResult(
        BigDecimal estimatedIncome,
        LocalDate priceSnapshotDate,
        int incomeScore,
        int insuranceScore,
        int farmingCareerScore,
        int totalScore,
        LocalDateTime calculatedAt
) {}
