package com.kkpp.admin.credit.service;

import com.kkpp.admin.credit.domain.CreditReviewApplication;
import com.kkpp.admin.credit.domain.CreditReviewAssScore;
import com.kkpp.admin.credit.domain.CreditReviewDocument;
import com.kkpp.admin.credit.domain.CreditReviewFarmerProfile;
import com.kkpp.admin.credit.domain.CreditReviewLimit;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.dto.ApproveCreditReviewRequest;
import com.kkpp.admin.credit.dto.CreditReviewDecisionResponse;
import com.kkpp.admin.credit.dto.CreditReviewDetailResponse;
import com.kkpp.admin.credit.dto.CreditReviewPageResponse;
import com.kkpp.admin.credit.dto.CreditReviewSummaryResponse;
import com.kkpp.admin.credit.dto.RejectCreditReviewRequest;
import com.kkpp.admin.credit.repository.CreditReviewApplicationRepository;
import com.kkpp.admin.credit.repository.CreditReviewAssScoreRepository;
import com.kkpp.admin.credit.repository.CreditReviewDocumentRepository;
import com.kkpp.admin.credit.repository.CreditReviewFarmerProfileRepository;
import com.kkpp.admin.credit.repository.CreditReviewLimitRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
// 관리자 한도 심사 기능의 비즈니스 로직을 담당하는 서비스
// 목록/상세 조회는 화면 표시용 DTO를 만들고, 승인/반려는 상태 변경과 한도 발급을 트랜잭션으로 처리한다.
public class CreditReviewService {

    // 목록 조회에서 비정상 page size가 들어왔을 때 사용할 기본값
    private static final int DEFAULT_PAGE_SIZE = 20;
    // 한 번에 너무 많은 데이터를 조회하지 않도록 제한하는 최대 페이지 크기
    private static final int MAX_PAGE_SIZE = 100;
    // 승인 요청에서 이율을 생략했을 때 사용하는 기본 연이율
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.0450");
    // 농지 면적을 m2에서 평 단위로 변환하기 위한 기준값
    private static final BigDecimal PYEONG_TO_M2 = new BigDecimal("3.305785");

    private final CreditReviewApplicationRepository applicationRepository;
    private final CreditReviewFarmerProfileRepository farmerProfileRepository;
    private final CreditReviewDocumentRepository documentRepository;
    private final CreditReviewAssScoreRepository assScoreRepository;
    private final CreditReviewLimitRepository limitRepository;
    private final DocumentUrlService documentUrlService;

    // 관리자 심사 목록을 페이지 단위로 조회한다.
    // Repository에서 바로 요약 DTO로 조회해 불필요한 엔티티 로딩을 줄인다.
    @Transactional(readOnly = true)
    public CreditReviewPageResponse getReviews(CreditReviewStatus status, int page, int size) {
        log.debug("한도 심사 목록 조회 요청: status={}, page={}, size={}", status, page, size);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.DESC, "appliedAt")
        );
        Page<CreditReviewSummaryResponse> result = applicationRepository.findReviewSummaries(status, pageable);

        log.debug(
                "한도 심사 목록 조회 완료: status={}, page={}, size={}, totalElements={}, totalPages={}",
                status,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );

        return new CreditReviewPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    // 관리자 심사 상세 화면에 필요한 여러 테이블의 데이터를 조합한다.
    // 신청 자체는 필수이고, 농지/ASS/서류 데이터는 없는 경우도 화면에서 처리할 수 있도록 null 또는 빈 목록으로 내려준다.
    @Transactional(readOnly = true)
    public CreditReviewDetailResponse getReview(UUID publicId) {
        log.debug("한도 심사 상세 조회 요청: applicationPublicId={}", publicId);

        CreditReviewApplication application = getApplication(publicId);
        CreditReviewFarmerProfile profile = farmerProfileRepository.findByUser_Id(application.getUser().getId())
                .orElse(null);
        CreditReviewAssScore assScore = assScoreRepository.findByApplication_Id(application.getId())
                .orElse(null);
        List<CreditReviewDocument> documents = documentRepository.findAllByApplication_IdOrderByIdAsc(application.getId());

        log.debug(
                "한도 심사 상세 조회 완료: applicationPublicId={}, hasFarmProfile={}, hasAssScore={}, documentCount={}",
                application.getPublicId(),
                profile != null,
                assScore != null,
                documents.size()
        );

        return toDetailResponse(application, profile, assScore, documents);
    }

    // 한도 신청을 승인한다.
    // 같은 신청에 대해 중복 승인되는 일을 막기 위해 신청 row를 비관적 락으로 조회하고, 이미 발급된 한도가 있는지 한 번 더 확인한다.
    @Transactional
    public CreditReviewDecisionResponse approve(UUID publicId, ApproveCreditReviewRequest request) {
        log.info("한도 승인 요청 수신: applicationPublicId={}, reviewedBy={}", publicId, request.reviewedBy());

        CreditReviewApplication application = getApplicationForUpdate(publicId);
        if (application.getStatus() != CreditReviewStatus.PENDING) {
            log.warn(
                    "한도 승인 요청 거부: applicationPublicId={}, reviewedBy={}, currentStatus={}",
                    publicId,
                    request.reviewedBy(),
                    application.getStatus()
            );
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "PENDING 상태의 한도 신청만 승인할 수 있습니다.");
        }
        if (limitRepository.existsByApplication_Id(application.getId())) {
            log.warn(
                    "한도 승인 요청 거부: applicationPublicId={}, reviewedBy={}, reason=이미 발급된 한도 존재",
                    publicId,
                    request.reviewedBy()
            );
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 한도가 발급된 신청입니다.");
        }

        LocalDateTime decidedAt = LocalDateTime.now();
        application.approve(request.reviewedBy(), request.approvedAmount(), decidedAt);

        CreditReviewLimit limit = CreditReviewLimit.issue(
                application,
                request.approvedAmount(),
                normalizeInterestRate(request.interestRate()),
                normalizePrincipalDueDate(request.principalDueDate()),
                normalizeExpiresAt(request.expiresAt())
        );
        CreditReviewLimit savedLimit = limitRepository.save(limit);

        log.info(
                "한도 승인 처리 완료: applicationPublicId={}, reviewedBy={}, approvedAmount={}, limitPublicId={}",
                application.getPublicId(),
                request.reviewedBy(),
                application.getApprovedAmount(),
                savedLimit.getPublicId()
        );

        return new CreditReviewDecisionResponse(
                application.getPublicId(),
                application.getStatus(),
                application.getApprovedAmount(),
                savedLimit.getPublicId(),
                application.getRejectionReason(),
                application.getDecidedAt()
        );
    }

    // 한도 신청을 반려한다.
    // 승인과 마찬가지로 신청 row를 잠근 뒤 PENDING 상태에서만 반려할 수 있도록 검증한다.
    @Transactional
    public CreditReviewDecisionResponse reject(UUID publicId, RejectCreditReviewRequest request) {
        log.info("한도 반려 요청 수신: applicationPublicId={}, reviewedBy={}", publicId, request.reviewedBy());

        CreditReviewApplication application = getApplicationForUpdate(publicId);
        if (application.getStatus() != CreditReviewStatus.PENDING) {
            log.warn(
                    "한도 반려 요청 거부: applicationPublicId={}, reviewedBy={}, currentStatus={}",
                    publicId,
                    request.reviewedBy(),
                    application.getStatus()
            );
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "PENDING 상태의 한도 신청만 반려할 수 있습니다.");
        }

        String rejectionReason = buildRejectionReason(request);
        application.reject(request.reviewedBy(), rejectionReason, LocalDateTime.now());

        log.info(
                "한도 반려 처리 완료: applicationPublicId={}, reviewedBy={}, reasonCode={}",
                application.getPublicId(),
                request.reviewedBy(),
                request.reasonCode()
        );

        return new CreditReviewDecisionResponse(
                application.getPublicId(),
                application.getStatus(),
                application.getApprovedAmount(),
                null,
                application.getRejectionReason(),
                application.getDecidedAt()
        );
    }

    // 단순 조회용 신청 조회 메서드
    // 상세 조회에서는 상태를 바꾸지 않으므로 락을 걸지 않는다.
    private CreditReviewApplication getApplication(UUID publicId) {
        return applicationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 한도 심사 신청입니다."));
    }

    // 상태 변경용 신청 조회 메서드
    // 승인/반려 요청이 동시에 들어왔을 때 한쪽만 처리되도록 DB row lock을 사용한다.
    private CreditReviewApplication getApplicationForUpdate(UUID publicId) {
        return applicationRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 한도 심사 신청입니다."));
    }

    // 여러 엔티티를 관리자 상세 화면 응답 DTO로 변환한다.
    // 화면이 필요한 구조에 맞게 신청자, 농지, ASS, 결정 정보, 서류 정보를 분리해서 담는다.
    private CreditReviewDetailResponse toDetailResponse(
            CreditReviewApplication application,
            CreditReviewFarmerProfile profile,
            CreditReviewAssScore assScore,
            List<CreditReviewDocument> documents
    ) {
        return new CreditReviewDetailResponse(
                application.getPublicId(),
                application.getStatus(),
                new CreditReviewDetailResponse.ApplicantInfo(
                        application.getUser().getPublicId(),
                        application.getUser().getName(),
                        application.getUser().getPhone(),
                        application.getUser().getAddress(),
                        application.getUser().getAddressDetail(),
                        application.getUser().getZipCode()
                ),
                toFarmInfo(profile),
                toAssInfo(application, assScore),
                new CreditReviewDetailResponse.ReviewDecisionInfo(
                        application.getApprovedAmount(),
                        application.getReviewedByAdminPublicId(),
                        application.getRejectionReason(),
                        application.getDecidedAt()
                ),
                documents.stream()
                        .map(document -> new CreditReviewDetailResponse.DocumentInfo(
                                document.getId(),
                                document.getDocumentType().name(),
                                documentUrlService.resolve(document.getFileUrl()),
                                document.getCreatedAt()
                        ))
                        .toList(),
                application.getAppliedAt()
        );
    }

    // 농지 프로필 엔티티를 상세 응답의 농지 정보로 변환한다.
    // DB에는 m2 단위로 저장되어 있으므로 화면 표시용 평 단위도 함께 계산한다.
    private CreditReviewDetailResponse.FarmInfo toFarmInfo(CreditReviewFarmerProfile profile) {
        if (profile == null) {
            return null;
        }
        return new CreditReviewDetailResponse.FarmInfo(
                profile.getFarmAddress(),
                profile.getFarmAddressDetail(),
                profile.getFarmZipCode(),
                profile.getFieldAreaM2(),
                toPyeong(profile.getFieldAreaM2()),
                profile.getMainCrop(),
                profile.getHasCropInsurance(),
                profile.getFarmingSince()
        );
    }

    // ASS 점수 엔티티를 상세 응답의 ASS 정보로 변환한다.
    // ASS 데이터가 아직 없더라도 신청 금액은 시스템 예상 한도처럼 화면에 표시할 수 있도록 유지한다.
    private CreditReviewDetailResponse.AssInfo toAssInfo(
            CreditReviewApplication application,
            CreditReviewAssScore assScore
    ) {
        if (assScore == null) {
            return new CreditReviewDetailResponse.AssInfo(
                    null,
                    application.getRequestedAmount(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return new CreditReviewDetailResponse.AssInfo(
                assScore.getEstimatedIncome(),
                application.getRequestedAmount(),
                assScore.getIncomeScore(),
                assScore.getInsuranceScore(),
                assScore.getFarmingCareerScore(),
                assScore.getTotalScore(),
                assScore.getPriceSnapshotDate(),
                assScore.getCalculatedAt()
        );
    }

    // 승인 요청에 이율이 없을 때 기본 이율을 보정한다.
    private BigDecimal normalizeInterestRate(BigDecimal interestRate) {
        return interestRate == null ? DEFAULT_INTEREST_RATE : interestRate;
    }

    // 승인 요청에 원금 상환 예정일이 없을 때 임시 기본 정책값을 넣는다.
    // 실제 상환 정책이 확정되면 작물별 정책 계산으로 교체할 수 있는 지점
    private LocalDate normalizePrincipalDueDate(LocalDate principalDueDate) {
        return principalDueDate == null ? LocalDate.now().plusMonths(8) : principalDueDate;
    }

    // 승인 요청에 만료일이 없을 때 1년 뒤를 기본 만료일로 사용한다.
    private LocalDate normalizeExpiresAt(LocalDate expiresAt) {
        return expiresAt == null ? LocalDate.now().plusYears(1) : expiresAt;
    }

    // 반려 사유 코드를 직접 입력 사유와 합쳐 DB의 rejection_reason 컬럼에 저장할 문자열을 만든다.
    // 둘 다 비어 있으면 관리자가 반려 사유를 입력하지 않은 것이므로 400 오류로 처리한다.
    private String buildRejectionReason(RejectCreditReviewRequest request) {
        String reasonCode = trimToNull(request.reasonCode());
        String reason = trimToNull(request.reason());
        if (reasonCode == null && reason == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "반려 사유를 입력해주세요.");
        }
        String rejectionReason = reasonCode == null ? reason : reasonCode + (reason == null ? "" : ": " + reason);
        if (rejectionReason.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "반려 사유는 500자 이하여야 합니다.");
        }
        return rejectionReason;
    }

    // 공백 문자열을 null로 통일해 반려 사유 조합 로직을 단순하게 만든다.
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    // m2 단위 농지 면적을 평 단위로 변환한다.
    // 소수점 둘째 자리까지 반올림해 관리자 화면에서 바로 표시하기 좋게 만든다.
    private BigDecimal toPyeong(BigDecimal fieldAreaM2) {
        if (fieldAreaM2 == null) {
            return null;
        }
        return fieldAreaM2.divide(PYEONG_TO_M2, 2, RoundingMode.HALF_UP);
    }

    // 요청 page size를 정상 범위로 보정한다.
    // 1보다 작은 값은 기본값으로, 최대값을 넘는 값은 MAX_PAGE_SIZE로 제한한다.
    private int normalizePageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
