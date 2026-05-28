package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewAssScore;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 한도 신청별 ASS 점수를 조회하는 Repository
// 상세 화면의 시스템 추출 데이터와 점수 영역을 채우는 데 사용된다.
public interface CreditReviewAssScoreRepository extends JpaRepository<CreditReviewAssScore, Long> {

    // credit_limit_applications.id를 기준으로 1건의 ASS 점수를 찾는다.
    Optional<CreditReviewAssScore> findByApplication_Id(Long applicationId);
}
