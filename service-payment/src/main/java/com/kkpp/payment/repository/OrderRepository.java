package com.kkpp.payment.repository;

import com.kkpp.payment.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPaymentRequestPublicId(UUID paymentRequestPublicId);
}

