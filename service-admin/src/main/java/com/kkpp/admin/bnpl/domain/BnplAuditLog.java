package com.kkpp.admin.bnpl.domain;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "audit_logs", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 관리자 행위 감사 로그를 저장하는 audit_logs 테이블 매핑 엔티티
// 알림 발송 시 반드시 action = "OVERDUE_ALERT_SENT" 또는 "REPAYMENT_ALERT_SENT" 로 기록한다.
public class BnplAuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "admin_user_public_id", nullable = false)
    private UUID adminUserPublicId;

    @Column(name = "user_public_id")
    private UUID userPublicId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_table", nullable = false, length = 100)
    private String targetTable;

    @Column(name = "target_public_id")
    private UUID targetPublicId;

    @Column(name = "before_data", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String afterData;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    public static BnplAuditLog create(
            UUID adminUserPublicId,
            UUID userPublicId,
            String action,
            String targetTable,
            UUID targetPublicId,
            String ipAddress
    ) {
        BnplAuditLog log = new BnplAuditLog();
        log.publicId = UUID.randomUUID();
        log.adminUserPublicId = adminUserPublicId;
        log.userPublicId = userPublicId;
        log.action = action;
        log.targetTable = targetTable;
        log.targetPublicId = targetPublicId;
        log.ipAddress = ipAddress;
        return log;
    }
}
