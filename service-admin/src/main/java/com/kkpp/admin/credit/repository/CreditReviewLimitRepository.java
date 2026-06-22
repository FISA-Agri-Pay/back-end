package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewLimit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 승인 후 발급되는 실제 한도 정보를 저장하고 조회하는 Repository
// 중복 승인 방지와 credit_limits row 생성을 담당한다.
public interface CreditReviewLimitRepository extends JpaRepository<CreditReviewLimit, Long> {

    // 이미 한도가 발급된 신청인지 확인한다.
    boolean existsByApplication_Id(Long applicationId);

    // 신청 ID 기준으로 발급된 한도를 조회한다.
    Optional<CreditReviewLimit> findByApplication_Id(Long applicationId);
}
