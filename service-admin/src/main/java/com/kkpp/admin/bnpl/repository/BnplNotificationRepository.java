package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplNotification;
import org.springframework.data.jpa.repository.JpaRepository;

// 알림 발송 이력 저장 Repository
// 알림 발송 결과(성공/실패 모두) 기록 및 마지막 발송 시각 조회에 사용된다.
public interface BnplNotificationRepository extends JpaRepository<BnplNotification, Long> {
}
