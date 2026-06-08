package com.kkpp.admin.credit.domain;

// 한도 신청 시 제출되는 농업 관련 서류 유형
// DB의 farmer_documents.document_type 체크 제약과 같은 값을 사용한다.
public enum CreditReviewDocumentType {
    AGRI_MANAGEMENT_REGISTRATION,
    CROP_DISASTER_INSURANCE
}
