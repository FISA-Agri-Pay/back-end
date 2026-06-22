package com.kkpp.admin.bnpl.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = "orders", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BnplOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "payment_request_public_id", nullable = false, unique = true)
    private UUID paymentRequestPublicId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;
}
