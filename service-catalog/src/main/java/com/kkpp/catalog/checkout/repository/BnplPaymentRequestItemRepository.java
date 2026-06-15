package com.kkpp.catalog.checkout.repository;

import com.kkpp.catalog.checkout.domain.BnplPaymentRequestItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BnplPaymentRequestItemRepository extends JpaRepository<BnplPaymentRequestItem, Long> {

    /**
     * 주어진 기간 동안 승인(APPROVED)된 결제요청 항목을 상품별로 집계하여,
     * 구매 수량 합계가 큰 순서대로 상품 publicId 목록을 반환한다.
     */
    @Query("""
            select i.productPublicId
            from BnplPaymentRequestItem i
            where i.paymentRequest.requestStatus = 'APPROVED'
              and i.paymentRequest.requestedAt >= :startInclusive
              and i.paymentRequest.requestedAt < :endExclusive
            group by i.productPublicId
            order by sum(i.quantity) desc
            """)
    List<UUID> findTopPurchasedProductIds(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );
}
