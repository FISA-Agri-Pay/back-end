package com.kkpp.admin.global.config;

// AbstractRoutingDataSource가 실제 연결할 DB를 고를 때 사용하는 키입니다.
// PRIMARY는 쓰기 가능한 원본 DB, REPLICA는 읽기 전용 복제 DB를 의미합니다.
public enum DataSourceLookupKey {
    PRIMARY,
    REPLICA
}
