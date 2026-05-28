package com.kkpp.admin.credit.dto;

import com.kkpp.admin.credit.domain.CreditReviewStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 한도 심사 상세 화면 전체를 구성하는 응답 DTO
// 신청자 정보, 농지 정보, ASS 정보, 심사 결정 정보, 제출 서류 정보를 한 번에 내려준다.
public record CreditReviewDetailResponse(
        UUID publicId,
        CreditReviewStatus status,
        ApplicantInfo applicant,
        FarmInfo farm,
        AssInfo ass,
        ReviewDecisionInfo decision,
        List<DocumentInfo> documents,
        LocalDateTime appliedAt
) {

    // 신청자의 기본 회원 정보를 나타내는 상세 응답 하위 DTO이다.
    public record ApplicantInfo(
            UUID userPublicId,
            String name,
            String phone,
            String address,
            String addressDetail,
            String zipCode
    ) {
    }

    // 신청자가 입력한 농지와 영농 정보를 나타내는 상세 응답 하위 DTO이다.
    public record FarmInfo(
            String farmAddress,
            String farmAddressDetail,
            String farmZipCode,
            BigDecimal fieldAreaM2,
            BigDecimal fieldAreaPyeong,
            String mainCrop,
            Boolean hasCropInsurance,
            Integer farmingSince
    ) {
    }

    // 시스템이 산정한 ASS 점수와 예상 한도를 나타내는 상세 응답 하위 DTO이다.
    public record AssInfo(
            BigDecimal estimatedIncome,
            BigDecimal systemEstimatedLimitAmount,
            Integer incomeScore,
            Integer insuranceScore,
            Integer farmingCareerScore,
            Integer totalScore,
            java.time.LocalDate priceSnapshotDate,
            LocalDateTime calculatedAt
    ) {
    }

    // 승인 또는 반려 결정 결과를 나타내는 상세 응답 하위 DTO이다.
    public record ReviewDecisionInfo(
            BigDecimal approvedAmount,
            Long reviewedBy,
            String rejectionReason,
            LocalDateTime decidedAt
    ) {
    }

    // 제출 서류 파일 정보를 나타내는 상세 응답 하위 DTO이다.
    public record DocumentInfo(
            Long id,
            String documentType,
            String fileUrl,
            LocalDateTime uploadedAt
    ) {
    }
}
