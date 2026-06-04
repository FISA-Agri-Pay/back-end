package com.kkpp.admin.order.controller;

import com.kkpp.admin.order.domain.DeliveryStatus;
import com.kkpp.admin.order.domain.OrderStatus;
import com.kkpp.admin.order.dto.AdminOrderPageResponse;
import com.kkpp.admin.order.dto.AdminOrderSummaryResponse;
import com.kkpp.admin.order.dto.UpdateOrderDeliveryStatusRequest;
import com.kkpp.admin.order.service.AdminOrderService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 주문", description = "관리자 주문 목록 조회 및 배송 상태 변경 API")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(
            summary = "관리자 주문 목록 조회",
            description = "주문 상태, 배송 상태, 주문일 기간, 사용자/수령인 검색어로 주문 목록을 페이지 단위로 조회합니다."
    )
    @GetMapping
    public ApiResponse<AdminOrderPageResponse> getOrders(
            @Parameter(description = "주문 상태: CONFIRMED / CANCELLED")
            @RequestParam(required = false) OrderStatus orderStatus,

            @Parameter(description = "배송 상태: PREPARING / SHIPPING / DELIVERED / CANCELLED")
            @RequestParam(required = false) DeliveryStatus deliveryStatus,

            @Parameter(description = "주문일 조회 시작일(YYYY-MM-DD)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(description = "주문일 조회 종료일(YYYY-MM-DD)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Parameter(description = "사용자명, 사용자 휴대폰, 수령인명, 수령인 연락처 검색어")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "페이지 번호(1부터 시작)")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지 크기(최대 100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                adminOrderService.getOrders(orderStatus, deliveryStatus, startDate, endDate, keyword, page, size),
                "관리자 주문 목록을 조회했습니다."
        );
    }

    @Operation(
            summary = "관리자 주문 배송 상태 변경",
            description = "주문 공개 ID 기준으로 배송 상태를 PREPARING, SHIPPING, DELIVERED, CANCELLED 중 하나로 변경합니다."
    )
    @PatchMapping("/{orderPublicId}/delivery-status")
    public ApiResponse<AdminOrderSummaryResponse> updateDeliveryStatus(
            @Parameter(description = "주문 공개 ID")
            @PathVariable UUID orderPublicId,

            @Valid @RequestBody UpdateOrderDeliveryStatusRequest request
    ) {
        return ApiResponse.success(
                adminOrderService.updateDeliveryStatus(orderPublicId, request),
                "주문 배송 상태를 변경했습니다."
        );
    }
}
