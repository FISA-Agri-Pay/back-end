package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewFarmerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 신청자별 농지 프로필을 조회하는 Repository
// 상세 화면의 농지 정보 영역을 채우는 데 사용된다.
public interface CreditReviewFarmerProfileRepository extends JpaRepository<CreditReviewFarmerProfile, Long> {

    // users.id를 기준으로 농지 프로필을 찾는다.
    Optional<CreditReviewFarmerProfile> findByUser_Id(Long userId);
}
