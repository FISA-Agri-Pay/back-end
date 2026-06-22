package com.kkpp.admin.order.service;

import com.kkpp.admin.order.domain.AdminOrder;
import com.kkpp.admin.order.domain.DeliveryStatus;
import com.kkpp.admin.order.domain.OrderStatus;
import com.kkpp.admin.order.dto.AdminOrderPageResponse;
import com.kkpp.admin.order.dto.AdminOrderSummaryResponse;
import com.kkpp.admin.order.dto.UpdateOrderDeliveryStatusRequest;
import com.kkpp.admin.order.repository.AdminOrderRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminOrderRepository adminOrderRepository;

    @Transactional(readOnly = true)
    public AdminOrderPageResponse getOrders(
            OrderStatus orderStatus,
            DeliveryStatus deliveryStatus,
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            int page,
            int size
    ) {
        try {
            validateDateRange(startDate, endDate);

            int currentPage = normalizePage(page);
            int pageSize = normalizePageSize(size);
            Pageable pageable = PageRequest.of(
                    currentPage - 1,
                    pageSize,
                    Sort.by(Sort.Direction.DESC, "orderedAt").and(Sort.by(Sort.Direction.DESC, "id"))
            );
            Specification<AdminOrder> specification = Specification
                    .where(orderStatusEquals(orderStatus))
                    .and(deliveryStatusEquals(deliveryStatus))
                    .and(orderedAtGreaterThanOrEqualTo(toStartAt(startDate)))
                    .and(orderedAtLessThan(toEndExclusiveAt(endDate)))
                    .and(keywordContains(keyword));
            Page<AdminOrderSummaryResponse> orders = adminOrderRepository.findAll(specification, pageable)
                    .map(AdminOrderSummaryResponse::from);

            log.info(
                    "관리자 주문 목록 조회 완료: 현재페이지={}, 페이지크기={}, 전체건수={}",
                    currentPage,
                    pageSize,
                    orders.getTotalElements()
            );
            return AdminOrderPageResponse.from(orders, currentPage, pageSize);
        } catch (BusinessException exception) {
            log.warn("관리자 주문 목록 조회 실패: {}", exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("관리자 주문 목록 조회 중 예외가 발생했습니다.", exception);
            throw exception;
        }
    }

    @Transactional
    public AdminOrderSummaryResponse updateDeliveryStatus(
            UUID orderPublicId,
            UpdateOrderDeliveryStatusRequest request
    ) {
        try {
            AdminOrder order = adminOrderRepository.findByPublicId(orderPublicId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 주문입니다."));

            DeliveryStatus previousStatus = order.getDeliveryStatus();
            order.changeDeliveryStatus(request.deliveryStatus());

            log.info(
                    "관리자 주문 배송 상태 변경 완료: 주문공개ID={}, 이전상태={}, 변경상태={}",
                    orderPublicId,
                    previousStatus,
                    order.getDeliveryStatus()
            );
            return AdminOrderSummaryResponse.from(order);
        } catch (BusinessException exception) {
            log.warn("관리자 주문 배송 상태 변경 실패: 주문공개ID={}, 사유={}", orderPublicId, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("관리자 주문 배송 상태 변경 중 예외가 발생했습니다. 주문공개ID={}", orderPublicId, exception);
            throw exception;
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private LocalDateTime toStartAt(LocalDate startDate) {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    private LocalDateTime toEndExclusiveAt(LocalDate endDate) {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    private boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.isBlank();
    }

    private String toKeywordPattern(String keyword) {
        if (!hasKeyword(keyword)) {
            return "%";
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private Specification<AdminOrder> orderStatusEquals(OrderStatus orderStatus) {
        return (root, query, criteriaBuilder) -> orderStatus == null
                ? null
                : criteriaBuilder.equal(root.get("orderStatus"), orderStatus);
    }

    private Specification<AdminOrder> deliveryStatusEquals(DeliveryStatus deliveryStatus) {
        return (root, query, criteriaBuilder) -> deliveryStatus == null
                ? null
                : criteriaBuilder.equal(root.get("deliveryStatus"), deliveryStatus);
    }

    private Specification<AdminOrder> orderedAtGreaterThanOrEqualTo(LocalDateTime startAt) {
        return (root, query, criteriaBuilder) -> startAt == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("orderedAt"), startAt);
    }

    private Specification<AdminOrder> orderedAtLessThan(LocalDateTime endAt) {
        return (root, query, criteriaBuilder) -> endAt == null
                ? null
                : criteriaBuilder.lessThan(root.get("orderedAt"), endAt);
    }

    private Specification<AdminOrder> keywordContains(String keyword) {
        if (!hasKeyword(keyword)) {
            return (root, query, criteriaBuilder) -> null;
        }

        String keywordPattern = toKeywordPattern(keyword);
        return (root, query, criteriaBuilder) -> {
            var user = root.join("user", JoinType.INNER);
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(user.get("name")), keywordPattern),
                    criteriaBuilder.like(user.get("phone"), keywordPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("recipientName")), keywordPattern),
                    criteriaBuilder.like(root.get("recipientPhone"), keywordPattern)
            );
        };
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
