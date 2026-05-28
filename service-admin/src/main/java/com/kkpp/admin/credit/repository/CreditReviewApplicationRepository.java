package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewApplication;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.dto.CreditReviewSummaryResponse;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 한도 심사 신청의 목록/상세/상태 변경 조회를 담당하는 Repository
// 관리자 화면에서 가장 많이 접근하는 credit_limit_applications 테이블을 중심으로 조회한다.
public interface CreditReviewApplicationRepository extends JpaRepository<CreditReviewApplication, Long> {

    // 목록 화면에서 필요한 값만 DTO로 바로 조회하는 쿼리이다.
    // 신청, 사용자, 농지 프로필, ASS 점수를 조인해 테이블 한 행에 필요한 정보를 만든다.
    @Query("""
            select new com.kkpp.admin.credit.dto.CreditReviewSummaryResponse(
                application.publicId,
                application.status,
                user.name,
                user.phone,
                profile.farmAddress,
                profile.fieldAreaM2,
                profile.mainCrop,
                application.requestedAmount,
                score.totalScore,
                application.reapplication,
                application.appliedAt
            )
            from CreditReviewApplication application
            join application.user user
            left join CreditReviewFarmerProfile profile on profile.user.id = user.id
            left join CreditReviewAssScore score on score.application.id = application.id
            where (:status is null or application.status = :status)
            order by application.appliedAt desc, application.id desc
            """)
    Page<CreditReviewSummaryResponse> findReviewSummaries(
            @Param("status") CreditReviewStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    // publicId로 상세 조회 대상 신청을 찾는다.
    // user는 상세 응답에서 항상 필요하므로 EntityGraph로 함께 로딩한다.
    Optional<CreditReviewApplication> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 승인/반려 처리에서 사용하는 조회 쿼리이다.
    // 동시에 같은 신청을 처리하지 못하도록 비관적 락을 건다.
    @Query("select application from CreditReviewApplication application join fetch application.user where application.publicId = :publicId")
    Optional<CreditReviewApplication> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
