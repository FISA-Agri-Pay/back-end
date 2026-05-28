package com.kkpp.admin.credit.dto;

import com.kkpp.admin.credit.domain.CreditReviewStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// 승인 또는 반려 처리 이후 반환하는 결정 결과 DTO
// 프론트엔드는 이 응답으로 처리된 상태, 승인 금액, 발급된 한도 publicId, 반려 사유를 확인한다.
public record CreditReviewDecisionResponse(
        UUID publicId,
        CreditReviewStatus status,
        BigDecimal approvedAmount,
        UUID creditLimitPublicId,
        String rejectionReason,
        LocalDateTime decidedAt
) {
}
