package com.kkpp.admin.credit.dto;

import jakarta.validation.constraints.Size;

// 관리자가 한도 신청을 반려할 때 보내는 요청 DTO
// reasonCode는 팝업의 선택 사유, reason은 직접 입력한 상세 사유를 담는다.
public record RejectCreditReviewRequest(
        Long reviewedBy,

        @Size(max = 50, message = "reasonCode must be 50 characters or fewer")
        String reasonCode,

        @Size(max = 500, message = "reason must be 500 characters or fewer")
        String reason
) {
}
