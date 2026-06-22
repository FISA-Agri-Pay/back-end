package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BnplOrderRepository extends JpaRepository<BnplOrder, Long> {

    @Query("""
            SELECT COUNT(o)
            FROM BnplOrder o
            WHERE o.paymentRequestPublicId IS NOT NULL
              AND o.orderStatus <> 'CANCELLED'
            """)
    long countBnplUsageOrders();

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM BnplOrder o
            WHERE o.paymentRequestPublicId IS NOT NULL
              AND o.orderStatus <> 'CANCELLED'
              AND o.orderedAt >= :startAt
              AND o.orderedAt < :endAt
            """)
    BigDecimal sumBnplOrderAmountBetween(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query(value = """
            SELECT
                CAST(o.ordered_at AS date) AS "usageDate",
                COALESCE(SUM(o.total_amount), 0) AS "amount"
            FROM core.orders o
            WHERE o.payment_request_public_id IS NOT NULL
              AND o.order_status <> 'CANCELLED'
              AND o.ordered_at >= :startAt
              AND o.ordered_at < :endAt
            GROUP BY CAST(o.ordered_at AS date)
            ORDER BY CAST(o.ordered_at AS date) ASC
            """, nativeQuery = true)
    List<BnplDailyUsageRow> findDailyBnplUsageBetween(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query(value = """
            SELECT
                o.public_id AS "orderPublicId",
                u.name AS "userName",
                COALESCE((
                    SELECT oi.product_name_snapshot
                    FROM core.order_items oi
                    WHERE oi.order_public_id = o.public_id
                    ORDER BY oi.id ASC
                    LIMIT 1
                ), '') AS "firstProductName",
                (
                    SELECT COUNT(*)
                    FROM core.order_items oi
                    WHERE oi.order_public_id = o.public_id
                ) AS "itemCount",
                o.total_amount AS "amount",
                o.ordered_at AS "orderedAt"
            FROM core.orders o
            JOIN core.users u ON u.public_id = o.user_public_id
            WHERE o.payment_request_public_id IS NOT NULL
              AND o.order_status <> 'CANCELLED'
            ORDER BY o.ordered_at DESC, o.id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<RecentBnplOrderRow> findRecentBnplOrders();

    interface BnplDailyUsageRow {

        LocalDate getUsageDate();

        BigDecimal getAmount();
    }

    interface RecentBnplOrderRow {

        UUID getOrderPublicId();

        String getUserName();

        String getFirstProductName();

        long getItemCount();

        BigDecimal getAmount();

        LocalDateTime getOrderedAt();
    }
}
