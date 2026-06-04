package com.kkpp.admin.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "관리자 주문 목록 페이지 응답")
public record AdminOrderPageResponse(
        List<AdminOrderSummaryResponse> orders,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static AdminOrderPageResponse from(
            Page<AdminOrderSummaryResponse> orders,
            int currentPage,
            int pageSize
    ) {
        return new AdminOrderPageResponse(
                orders.getContent(),
                currentPage,
                pageSize,
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.isFirst(),
                orders.isLast()
        );
    }
}
