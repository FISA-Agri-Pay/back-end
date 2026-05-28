package com.kkpp.admin.credit.dto;

import com.kkpp.admin.credit.domain.CreditReviewStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// 한도 심사 목록의 한 행을 나타내는 요약 응답 DTO
// 목록 화면에서 바로 보여줄 신청자, 농지, ASS 핵심 정보만 담는다.
public record CreditReviewSummaryResponse(
        UUID publicId,
        CreditReviewStatus status,
        String applicantName,
        String phone,
        String farmAddress,
        BigDecimal fieldAreaM2,
        String mainCrop,
        BigDecimal systemEstimatedLimitAmount,
        Integer assTotalScore,
        Boolean reapplication,
        LocalDateTime appliedAt
) {
}
