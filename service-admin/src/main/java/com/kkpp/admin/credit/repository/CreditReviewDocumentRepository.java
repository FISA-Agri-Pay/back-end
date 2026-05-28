package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 한도 신청에 첨부된 제출 서류 목록을 조회하는 Repository
// 농업경영체 등록확인서와 보험 증빙 파일 URL을 가져오는 데 사용된다.
public interface CreditReviewDocumentRepository extends JpaRepository<CreditReviewDocument, Long> {

    // 신청 ID 기준으로 서류를 정렬 조회한다.
    // 관리자 화면은 이 목록 중 FARM_MANAGEMENT 파일을 서류 뷰어에 표시할 수 있다.
    List<CreditReviewDocument> findAllByApplication_IdOrderByIdAsc(Long applicationId);
}
