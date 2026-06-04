package com.kkpp.core.credithistory.repository;

import com.kkpp.core.credithistory.domain.CreditUsageLedger;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditUsageHistoryRepository extends JpaRepository<CreditUsageLedger, Long> {

    // service-core 소유 테이블만 조인합니다. 상품명은 catalog.products가 아니라 주문 시점 스냅샷을 사용합니다.
    // 사용자 소유 검증은 orders.user_public_id 기준으로 제한하고, 원장은 order_public_id로 연결합니다.
    // 대표 상품명은 첫 번째 주문 상품 스냅샷을 사용하고, 나머지 개수는 서비스에서 "외 N개"로 표시합니다.
    @Query(value = """
            SELECT
                cul.public_id AS historyPublicId,
                cul.used_at AS usedAt,
                cul.amount AS amount,
                cul.usage_type AS usageType,
                o.order_status AS orderStatus,
                o.delivery_status AS deliveryStatus,
                (
                    SELECT oi.product_name_snapshot
                    FROM core.order_items oi
                    WHERE oi.order_public_id = o.public_id
                    ORDER BY oi.id ASC
                    LIMIT 1
                ) AS firstProductName,
                (
                    SELECT COUNT(*)
                    FROM core.order_items oi
                    WHERE oi.order_public_id = o.public_id
                ) AS itemCount
            FROM core.credit_usage_ledger cul
            JOIN core.orders o ON o.public_id = cul.order_public_id
            WHERE o.user_public_id = :userPublicId
            ORDER BY cul.used_at DESC, cul.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CreditUsageHistoryRow> findLatestUsageHistories(
            @Param("userPublicId") UUID userPublicId,
            @Param("limit") int limit
    );

    interface CreditUsageHistoryRow {

        UUID getHistoryPublicId();

        LocalDateTime getUsedAt();

        BigDecimal getAmount();

        String getUsageType();

        String getOrderStatus();

        String getDeliveryStatus();

        String getFirstProductName();

        long getItemCount();
    }
}
