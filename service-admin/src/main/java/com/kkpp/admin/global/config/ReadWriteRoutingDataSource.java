package com.kkpp.admin.global.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Spring이 DB 커넥션을 요청하는 순간, 현재 트랜잭션 속성을 보고 primary/replica 중 하나를 선택합니다.
// 핵심 기준은 서비스 계층의 @Transactional(readOnly = true) 여부입니다.
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        // @Transactional(readOnly = true)로 시작된 조회 전용 트랜잭션이면 replica DB로 라우팅합니다.
        // 예: DashboardService#getSummary(), BnplAdminService#getBnplSummary() 같은 조회 API
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return DataSourceLookupKey.REPLICA;
        }

        // readOnly가 아닌 일반 트랜잭션은 데이터 변경 가능성이 있으므로 primary DB로 라우팅합니다.
        // 예: 승인/반려, 알림 발송, 상품 등록/수정/삭제 같은 쓰기 API
        return DataSourceLookupKey.PRIMARY;
    }
}
