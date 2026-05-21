package com.kkpp.batch.bss.domain;

import java.math.BigDecimal;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credit_limits", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long applicationId;

    @Column(nullable = false)
    private BigDecimal totalLimit;

    @Column(nullable = false)
    private BigDecimal usedAmount;

    @Column(nullable = false)
    private String status;
}
