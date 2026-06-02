package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

// 관리자 행위 감사 로그 저장 Repository
// 알림 발송 시 action = "OVERDUE_ALERT_SENT" 또는 "REPAYMENT_ALERT_SENT" 로 반드시 기록한다.
public interface BnplAuditLogRepository extends JpaRepository<BnplAuditLog, Long> {
}
