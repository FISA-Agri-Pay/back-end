package com.kkpp.admin.dashboard.controller;

import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse;
import com.kkpp.admin.dashboard.service.DashboardService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 대시보드", description = "관리자 대시보드 주요 지표 조회 API")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "관리자 대시보드 요약 조회",
            description = "한도 심사 대기 건수, 당월 외상 결제 총액, 당월 상환 예정 금액, 현재 연체율, 최근 7일 외상 이용 추이, 최근 외상 주문, 관리자 처리 필요 업무를 조회합니다."
    )
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getDashboardSummary() {
        return ApiResponse.success(dashboardService.getSummary(), "관리자 대시보드 요약 정보를 조회했습니다.");
    }
}
