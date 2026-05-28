package com.kkpp.admin.credit.domain;

// 한도 심사 신청의 진행 상태를 나타내는 enum
// DB의 credit_limit_applications.status 체크 제약과 같은 값을 사용한다.
public enum CreditReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
