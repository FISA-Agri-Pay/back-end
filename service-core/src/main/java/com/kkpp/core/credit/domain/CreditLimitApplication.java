package com.kkpp.core.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_limit_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimitApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    public static CreditLimitApplication create(Long userId) {
        CreditLimitApplication application = new CreditLimitApplication();
        application.publicId = UUID.randomUUID();
        application.userId = userId;
        application.status = ApplicationStatus.PENDING;
        application.appliedAt = LocalDateTime.now();
        return application;
    }
}
