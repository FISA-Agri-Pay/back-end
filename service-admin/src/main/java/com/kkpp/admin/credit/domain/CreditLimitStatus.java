package com.kkpp.admin.credit.domain;

// 관리자 승인 후 발급된 실제 한도의 상태를 나타내는 enum
// DB의 credit_limits.status 체크 제약과 같은 값을 사용한다.
public enum CreditLimitStatus {
    ACTIVE,
    SUSPENDED,
    REPAID,
    EXPIRED
}
