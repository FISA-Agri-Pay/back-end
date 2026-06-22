package com.kkpp.admin.credit.controller;

import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.dto.ApproveCreditReviewRequest;
import com.kkpp.admin.credit.dto.CreditReviewDecisionResponse;
import com.kkpp.admin.credit.dto.CreditReviewDetailResponse;
import com.kkpp.admin.credit.dto.CreditReviewPageResponse;
import com.kkpp.admin.credit.dto.RejectCreditReviewRequest;
import com.kkpp.admin.credit.service.CreditReviewService;
import com.kkpp.common.core.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/credit-reviews")
@RequiredArgsConstructor
// 관리자 한도 심사 화면에서 사용하는 REST API 진입점
// URL 명세의 base url인 /api/v1/admin/credit-reviews를 담당한다.
public class CreditReviewController {

    private final CreditReviewService creditReviewService;

    // 심사 목록 조회 API
    // status가 없으면 전체 심사 목록을 조회하고, status가 있으면 해당 상태만 필터링한다.
    // page와 size는 관리자 화면의 페이지네이션에 사용된다.
    @GetMapping
    public ApiResponse<CreditReviewPageResponse> getReviews(
            @RequestParam(defaultValue = "PENDING") CreditReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(creditReviewService.getReviews(status, page, size));
    }

    // 심사 상세 조회 API
    // 신청 publicId를 기준으로 신청자 정보, 농지 정보, ASS 점수, 제출 서류 목록을 한 번에 내려준다.
    @GetMapping("/{publicId}")
    public ApiResponse<CreditReviewDetailResponse> getReview(@PathVariable UUID publicId) {
        return ApiResponse.success(creditReviewService.getReview(publicId));
    }

    // 한도 최종 승인 API
    // 관리자가 입력한 최종 승인 금액으로 신청 상태를 APPROVED로 바꾸고 실제 credit_limits 데이터를 생성한다.
    @PatchMapping("/{publicId}/approve")
    public ApiResponse<CreditReviewDecisionResponse> approve(
            @PathVariable UUID publicId,
            @Valid @RequestBody ApproveCreditReviewRequest request
    ) {
        return ApiResponse.success(creditReviewService.approve(publicId, request), "한도 신청이 승인되었습니다.");
    }

    // 반려 처리 API
    // 관리자가 선택하거나 직접 입력한 반려 사유를 저장하고 신청 상태를 REJECTED로 바꾼다.
    @PatchMapping("/{publicId}/reject")
    public ApiResponse<CreditReviewDecisionResponse> reject(
            @PathVariable UUID publicId,
            @Valid @RequestBody RejectCreditReviewRequest request
    ) {
        return ApiResponse.success(creditReviewService.reject(publicId, request), "한도 신청이 반려되었습니다.");
    }
}
