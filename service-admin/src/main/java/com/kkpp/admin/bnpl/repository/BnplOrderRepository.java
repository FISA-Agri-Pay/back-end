package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BnplOrderRepository extends JpaRepository<BnplOrder, Long> {

    @Query("""
            SELECT COUNT(o)
            FROM BnplOrder o
            WHERE o.paymentRequestPublicId IS NOT NULL
              AND o.orderStatus <> 'CANCELLED'
            """)
    long countBnplUsageOrders();
}
