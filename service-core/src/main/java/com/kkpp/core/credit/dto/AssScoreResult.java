package com.kkpp.core.credit.dto;

public record AssScoreResult(
        int fieldAreaScore,
        int cropScore,
        int insuranceScore,
        int farmingCareerScore
) {}
