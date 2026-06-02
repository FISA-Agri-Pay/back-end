package com.kkpp.admin.bnpl.controller;

import com.kkpp.admin.bnpl.domain.OverdueStage;
import com.kkpp.admin.bnpl.dto.BnplApiResponse;
import com.kkpp.admin.bnpl.dto.BnplSummaryResponse;
import com.kkpp.admin.bnpl.dto.BnplUserPageResponse;
import com.kkpp.admin.bnpl.dto.OverdueAlertRequest;
import com.kkpp.admin.bnpl.dto.OverdueAlertResponse;
import com.kkpp.admin.bnpl.dto.OverdueSummaryResponse;
import com.kkpp.admin.bnpl.dto.OverdueUserPageResponse;
import com.kkpp.admin.bnpl.dto.RepaymentAlertRequest;
import com.kkpp.admin.bnpl.dto.RepaymentAlertResponse;
import com.kkpp.admin.bnpl.service.BnplAdminService;
import com.kkpp.common.security.auth.AuthUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BNPL 관리", description = "관리자 BNPL 이용/연체 현황 조회 및 알림 발송 API")
@RestController
@RequestMapping("/api/v1/admin/bnpl")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class BnplAdminController {

    private final BnplAdminService bnplAdminService;

    @Operation(summary = "이용 현황 KPI 조회", description = "총 이용 잔액 / 당월 회수 예정액 / 연체 금액 3개 KPI 카드 데이터를 반환합니다.")
    @GetMapping("/summary")
    public BnplApiResponse<BnplSummaryResponse> getBnplSummary() {
        return BnplApiResponse.success(bnplAdminService.getBnplSummary());
    }

    @Operation(summary = "사용자별 이용 현황 목록", description = "BNPL 한도를 보유한 사용자 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/users")
    public BnplApiResponse<BnplUserPageResponse> getBnplUsers(
            @Parameter(description = "조회 시작일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "이름 또는 연락처 검색") @RequestParam(required = false) String search,
            @Parameter(description = "상태 필터: ALL / NORMAL / OVERDUE / SUSPENDED") @RequestParam(defaultValue = "ALL") String status,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 건수 (최대 100)") @RequestParam(defaultValue = "20") int size
    ) {
        return BnplApiResponse.success(bnplAdminService.getBnplUsers(startDate, endDate, search, status, page, size));
    }

    @Operation(summary = "상환 알림 단건 발송", description = "특정 사용자에게 상환 알림을 발송합니다. 발송 이력은 notifications와 audit_logs에 기록됩니다. JWT 인증 필요.")
    @PostMapping("/users/{userPublicId}/repayment-alert")
    public BnplApiResponse<RepaymentAlertResponse> sendRepaymentAlert(
            @Parameter(description = "사용자 public_id (UUID)") @PathVariable UUID userPublicId,
            @Valid @RequestBody RepaymentAlertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        return BnplApiResponse.success(bnplAdminService.sendRepaymentAlert(userPublicId, request, extractAuthUser(authentication), clientIp));
    }

    @Operation(summary = "연체 현황 KPI 조회", description = "연체 회원 수 / 총 연체 금액 / 총 연체 이자 / 단계별 현황 KPI 카드 데이터를 반환합니다.")
    @GetMapping("/overdue/summary")
    public BnplApiResponse<OverdueSummaryResponse> getOverdueSummary() {
        return BnplApiResponse.success(bnplAdminService.getOverdueSummary());
    }

    @Operation(summary = "연체 대상자 목록", description = "미해소 연체(resolved_at IS NULL) 대상자를 페이지네이션으로 조회합니다.")
    @GetMapping("/overdue/users")
    public BnplApiResponse<OverdueUserPageResponse> getOverdueUsers(
            @Parameter(description = "조회 시작일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "연체 유형: ALL / INTEREST / PRINCIPAL") @RequestParam(defaultValue = "ALL") String overdueType,
            @Parameter(description = "연체 단계: STAGE_1(1~30일) / STAGE_2(31~60일) / STAGE_3(61일+)") @RequestParam(required = false) OverdueStage stage,
            @Parameter(description = "최소 연체 일수") @RequestParam(required = false) Integer minDays,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 건수 (최대 100)") @RequestParam(defaultValue = "20") int size
    ) {
        return BnplApiResponse.success(bnplAdminService.getOverdueUsers(startDate, endDate, overdueType, stage, minDays, page, size));
    }

    @Operation(summary = "연체 알림 일괄 발송", description = "연체 대상자에게 일괄 알림을 발송합니다. userPublicIds 미입력 시 전체 미해소 연체자 대상. JWT 인증 필요.")
    @PostMapping("/overdue/alerts")
    public BnplApiResponse<OverdueAlertResponse> sendOverdueAlerts(
            @Valid @RequestBody OverdueAlertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        return BnplApiResponse.success(bnplAdminService.sendOverdueAlerts(request, extractAuthUser(authentication), clientIp));
    }

    private AuthUserInfo extractAuthUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserInfo authUserInfo) {
            return authUserInfo;
        }
        return null;
    }

    // X-Forwarded-For 헤더를 우선 확인하여 실제 클라이언트 IP를 추출한다.
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
