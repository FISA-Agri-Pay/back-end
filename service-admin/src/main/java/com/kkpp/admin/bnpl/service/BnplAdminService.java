package com.kkpp.admin.bnpl.service;

import com.kkpp.admin.bnpl.domain.BnplAuditLog;
import com.kkpp.admin.bnpl.domain.BnplCreditLimit;
import com.kkpp.admin.bnpl.domain.BnplNotification;
import com.kkpp.admin.bnpl.domain.BnplUser;
import com.kkpp.admin.bnpl.domain.NotificationChannel;
import com.kkpp.admin.bnpl.domain.OverdueStage;
import com.kkpp.admin.bnpl.domain.OverdueType;
import com.kkpp.admin.bnpl.dto.BnplSummaryResponse;
import com.kkpp.admin.bnpl.dto.BnplUserPageResponse;
import com.kkpp.admin.bnpl.dto.BnplUserSummaryResponse;
import com.kkpp.admin.bnpl.dto.OverdueAlertItemResponse;
import com.kkpp.admin.bnpl.dto.OverdueAlertRequest;
import com.kkpp.admin.bnpl.dto.OverdueAlertResponse;
import com.kkpp.admin.bnpl.dto.OverdueSummaryResponse;
import com.kkpp.admin.bnpl.dto.OverdueUserPageResponse;
import com.kkpp.admin.bnpl.dto.PaginationInfo;
import com.kkpp.admin.bnpl.dto.RepaymentAlertRequest;
import com.kkpp.admin.bnpl.dto.RepaymentAlertResponse;
import com.kkpp.admin.bnpl.repository.BnplAuditLogRepository;
import com.kkpp.admin.bnpl.repository.BnplAdminUserRepository;
import com.kkpp.admin.bnpl.repository.BnplCreditLimitRepository;
import com.kkpp.admin.bnpl.repository.BnplNotificationRepository;
import com.kkpp.admin.bnpl.repository.BnplUserRepository;
import com.kkpp.admin.bnpl.repository.InterestLedgerRepository;
import com.kkpp.admin.bnpl.repository.LoanOverdueLedgerRepository;
import com.kkpp.admin.bnpl.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.security.auth.AuthUserInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
// 관리자 BNPL 이용/연체 현황 조회 및 알림 발송 비즈니스 로직을 담당하는 서비스
public class BnplAdminService {

    // 연체 임계값 초과 여부를 판단하는 기준 금액 (3천만 원)
    private static final BigDecimal OVERDUE_ALERT_THRESHOLD = new BigDecimal("30000000");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(9999, 12, 31);

    private final BnplCreditLimitRepository creditLimitRepository;
    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final LoanOverdueLedgerRepository overdueRepository;
    private final BnplNotificationRepository notificationRepository;
    private final BnplAuditLogRepository auditLogRepository;
    private final BnplAdminUserRepository adminUserRepository;
    private final BnplUserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    // API 1 — 이용 현황 KPI 조회
    // 총 이용 잔액 / 당월 회수 예정액 / 연체 금액 세 가지 수치를 한 번에 산정한다.
    @Transactional(readOnly = true)
    public BnplSummaryResponse getBnplSummary() {
        log.debug("BNPL 이용 현황 KPI 조회 요청");

        BigDecimal totalBalance = creditLimitRepository.sumActiveUsedAmount();

        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();
        BigDecimal scheduledInterest = interestLedgerRepository.sumScheduledRepaymentThisMonth(startOfMonth, endOfMonth);
        BigDecimal scheduledPrincipal = principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(startOfMonth, endOfMonth);
        BigDecimal scheduledRepayment = scheduledInterest.add(scheduledPrincipal);

        BigDecimal overdueAmount = overdueRepository.sumUnresolvedOverdueAmount();
        boolean isOverdueAlert = overdueAmount.compareTo(OVERDUE_ALERT_THRESHOLD) > 0;

        log.debug("BNPL 이용 현황 KPI 조회 완료: totalBalance={}, scheduledRepayment={}, overdueAmount={}, isOverdueAlert={}",
                totalBalance, scheduledRepayment, overdueAmount, isOverdueAlert);

        return new BnplSummaryResponse(totalBalance, scheduledRepayment, overdueAmount, isOverdueAlert);
    }

    // API 2 — 사용자별 BNPL 이용 현황 목록 조회
    // startDate/endDate는 credit_limits.created_at 기준, search는 이름·연락처 LIKE 검색이다.
    // page는 1-indexed(API 명세)를 Spring Data의 0-indexed로 변환하여 조회한다.
    @Transactional(readOnly = true)
    public BnplUserPageResponse getBnplUsers(
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String status,
            int page,
            int size
    ) {
        log.debug("BNPL 사용자 목록 조회 요청: status={}, page={}, size={}", status, page, size);

        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                normalizePageSize(size)
        );

        String normalizedSearch = trimToNull(search);
        String searchPattern = normalizedSearch == null ? null : "%" + normalizedSearch + "%";
        LocalDateTime startDateTime = normalizeStartDate(startDate).atStartOfDay();
        LocalDateTime endDateTime = normalizeEndDate(endDate).atTime(23, 59, 59);
        String normalizedStatus = status == null ? "ALL" : status.toUpperCase();

        Page<BnplUserSummaryResponse> result = creditLimitRepository.findBnplUsers(
                normalizedSearch,
                searchPattern,
                startDateTime,
                endDateTime,
                normalizedStatus,
                pageable
        );

        log.debug("BNPL 사용자 목록 조회 완료: totalElements={}, totalPages={}",
                result.getTotalElements(), result.getTotalPages());

        return new BnplUserPageResponse(
                result.getContent(),
                new PaginationInfo(result.getNumber() + 1, result.getTotalPages(), result.getTotalElements())
        );
    }

    // API 3 — 상환 알림 단건 발송
    // notifications 테이블에 이력을 저장하고 audit_logs에 관리자 행위를 기록한다.
    @Transactional
    public RepaymentAlertResponse sendRepaymentAlert(
            UUID userPublicId,
            RepaymentAlertRequest request,
            AuthUserInfo authUser,
            String clientIp
    ) {
        log.info("상환 알림 단건 발송 요청: userPublicId={}, channel={}", userPublicId, request.channel());

        UUID adminUserPublicId = resolveAdminUserPublicId(authUser);
        NotificationChannel channel = parseChannel(request.channel());

        BnplCreditLimit creditLimit = creditLimitRepository.findActiveByUserPublicId(userPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "활성 상태의 BNPL 한도를 보유한 사용자를 찾을 수 없습니다."));
        BnplUser user = findUserForAlert(userPublicId);
        validateReceivablePhone(user);

        String content = resolveAlertMessage(request.message(), channel, "REPAYMENT");

        BnplNotification notification = BnplNotification.create(
                userPublicId,
                "상환 안내",
                content,
                "REPAYMENT_ALERT_" + channel.name()
        );
        BnplNotification saved = notificationRepository.save(notification);

        BnplAuditLog auditLog = BnplAuditLog.create(
                adminUserPublicId,
                userPublicId,
                "REPAYMENT_ALERT_SENT",
                "notifications",
                saved.getPublicId(),
                clientIp
        );
        auditLogRepository.save(auditLog);

        Instant sentAt = Instant.now();
        log.info("상환 알림 단건 발송 완료: userPublicId={}, channel={}, sentAt={}", userPublicId, channel, sentAt);

        return new RepaymentAlertResponse(sentAt, channel.name());
    }

    // API 4 — 연체 현황 KPI 조회
    // 연체 회원 수 / 총 연체 금액 / 총 연체 이자 / 단계별 현황을 산정한다.
    @Transactional(readOnly = true)
    public OverdueSummaryResponse getOverdueSummary() {
        log.debug("연체 현황 KPI 조회 요청");

        long totalOverdueUsers = overdueRepository.countDistinctOverdueUsers();
        BigDecimal totalOverdueAmount = overdueRepository.sumUnresolvedOverdueAmount();
        BigDecimal totalPenaltyAmount = overdueRepository.sumUnresolvedPenaltyAmount();

        List<Object[]> stageRows = overdueRepository.countByStage();
        Map<OverdueStage, Long> stageMap = stageRows.stream()
                .collect(Collectors.toMap(
                        row -> (OverdueStage) row[0],
                        row -> (Long) row[1]
                ));
        OverdueSummaryResponse.OverdueByStage overdueByStage = new OverdueSummaryResponse.OverdueByStage(
                stageMap.getOrDefault(OverdueStage.STAGE_1, 0L),
                stageMap.getOrDefault(OverdueStage.STAGE_2, 0L),
                stageMap.getOrDefault(OverdueStage.STAGE_3, 0L)
        );

        log.debug("연체 현황 KPI 조회 완료: totalOverdueUsers={}, totalOverdueAmount={}", totalOverdueUsers, totalOverdueAmount);

        return new OverdueSummaryResponse(totalOverdueUsers, totalOverdueAmount, totalPenaltyAmount, overdueByStage);
    }

    // API 5 — 연체 대상자 목록 조회
    // overdueType, stage, minDays 필터와 날짜 범위로 미해소 연체 건을 페이지 조회한다.
    @Transactional(readOnly = true)
    public OverdueUserPageResponse getOverdueUsers(
            LocalDate startDate,
            LocalDate endDate,
            String overdueType,
            OverdueStage stage,
            Integer minDays,
            int page,
            int size
    ) {
        log.debug("연체 대상자 목록 조회 요청: overdueType={}, stage={}, minDays={}, page={}, size={}",
                overdueType, stage, minDays, page, size);
        OverdueType parsedOverdueType = parseOverdueType(overdueType);

        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                normalizePageSize(size)
        );

        Page<com.kkpp.admin.bnpl.dto.OverdueUserSummaryResponse> result = overdueRepository.findOverdueUsers(
                parsedOverdueType,
                stage,
                minDays,
                normalizeStartDate(startDate),
                normalizeEndDate(endDate),
                pageable
        );

        log.debug("연체 대상자 목록 조회 완료: totalElements={}, totalPages={}",
                result.getTotalElements(), result.getTotalPages());

        return new OverdueUserPageResponse(
                result.getContent(),
                new PaginationInfo(result.getNumber() + 1, result.getTotalPages(), result.getTotalElements())
        );
    }

    // API 6 — 연체 알림 일괄 발송
    // userPublicIds 미입력 시 전체 미해소 연체자 대상, 발송 성공/실패 모두 기록한다.
    public OverdueAlertResponse sendOverdueAlerts(OverdueAlertRequest request, AuthUserInfo authUser, String clientIp) {
        log.info("연체 알림 일괄 발송 요청: channel={}, targetCount={}",
                request.channel(),
                request.userPublicIds() == null ? "전체" : request.userPublicIds().size());

        UUID adminUserPublicId = resolveAdminUserPublicId(authUser);
        NotificationChannel channel = parseChannel(request.channel());
        List<UUID> targetUserPublicIds = resolveTargetUsers(request.userPublicIds());
        String content = resolveAlertMessage(request.message(), channel, "OVERDUE");

        List<OverdueAlertItemResponse> results = new ArrayList<>();

        for (UUID userPublicId : targetUserPublicIds) {
            OverdueAlertItemResponse item = sendSingleOverdueAlertInNewTransaction(
                    userPublicId,
                    channel,
                    content,
                    adminUserPublicId,
                    clientIp
            );
            results.add(item);
        }

        long successCount = results.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        long failCount = results.size() - successCount;

        log.info("연체 알림 일괄 발송 완료: totalCount={}, successCount={}, failCount={}",
                results.size(), successCount, failCount);

        return new OverdueAlertResponse(results.size(), (int) successCount, (int) failCount, results);
    }

    // 사용자 1명에게 연체 알림을 발송하고 결과를 반환한다.
    // 개별 실패가 전체 트랜잭션을 롤백하지 않도록 예외를 잡아서 FAIL 결과로 처리한다.
    private OverdueAlertItemResponse sendSingleOverdueAlertInNewTransaction(
            UUID userPublicId,
            NotificationChannel channel,
            String content,
            UUID adminUserPublicId,
            String clientIp
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return transactionTemplate.execute(status ->
                    sendSingleOverdueAlert(userPublicId, channel, content, adminUserPublicId, clientIp)
            );
        } catch (Exception e) {
            log.warn("연체 알림 발송 트랜잭션 실패: userPublicId={}, reason={}", userPublicId, e.getMessage());
            return new OverdueAlertItemResponse(userPublicId.toString(), "FAIL", null, e.getMessage());
        }
    }

    private OverdueAlertItemResponse sendSingleOverdueAlert(
            UUID userPublicId,
            NotificationChannel channel,
            String content,
            UUID adminUserPublicId,
            String clientIp
    ) {
        try {
            BnplUser user = findUserForAlert(userPublicId);
            if (!hasReceivablePhone(user)) {
                return recordFailedOverdueAlert(userPublicId, channel, "번호 없음", adminUserPublicId, clientIp);
            }

            BnplNotification notification = BnplNotification.create(
                    userPublicId,
                    "연체 안내",
                    content,
                    "OVERDUE_ALERT_" + channel.name()
            );
            BnplNotification saved = notificationRepository.save(notification);

            BnplAuditLog auditLog = BnplAuditLog.create(
                    adminUserPublicId,
                    userPublicId,
                    "OVERDUE_ALERT_SENT",
                    "notifications",
                    saved.getPublicId(),
                    clientIp
            );
            auditLogRepository.save(auditLog);

            return new OverdueAlertItemResponse(userPublicId.toString(), "SUCCESS", Instant.now(), null);
        } catch (Exception e) {
            log.warn("연체 알림 발송 실패: userPublicId={}, reason={}", userPublicId, e.getMessage());
            return new OverdueAlertItemResponse(userPublicId.toString(), "FAIL", null, e.getMessage());
        }
    }

    private OverdueAlertItemResponse recordFailedOverdueAlert(
            UUID userPublicId,
            NotificationChannel channel,
            String reason,
            UUID adminUserPublicId,
            String clientIp
    ) {
        BnplNotification notification = BnplNotification.create(
                userPublicId,
                "연체 안내 실패",
                reason,
                "OVERDUE_ALERT_FAIL_" + channel.name()
        );
        BnplNotification saved = notificationRepository.save(notification);

        BnplAuditLog auditLog = BnplAuditLog.create(
                adminUserPublicId,
                userPublicId,
                "OVERDUE_ALERT_SENT",
                "notifications",
                saved.getPublicId(),
                clientIp
        );
        auditLogRepository.save(auditLog);

        return new OverdueAlertItemResponse(userPublicId.toString(), "FAIL", null, reason);
    }

    // 일괄 발송 대상 사용자 UUID 목록을 결정한다.
    // userPublicIds가 비어 있으면 미해소 연체자 전체를 대상으로 한다.
    private List<UUID> resolveTargetUsers(List<String> userPublicIds) {
        if (userPublicIds == null || userPublicIds.isEmpty()) {
            return overdueRepository.findUnresolvedUserPublicIds();
        }
        return userPublicIds.stream()
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효하지 않은 사용자 ID 형식입니다: " + id);
                    }
                })
                .toList();
    }

    private UUID resolveAdminUserPublicId(AuthUserInfo authUser) {
        if (authUser == null) {
            authUser = currentAuthUser();
        }
        if (authUser == null || authUser.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 인증 정보가 필요합니다.");
        }
        return adminUserRepository.findById(authUser.userId())
                .map(adminUser -> {
                    if (!"ACTIVE".equals(adminUser.getStatus())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "활성 상태의 관리자가 아닙니다.");
                    }
                    return adminUser.getPublicId();
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 정보를 찾을 수 없습니다."));
    }

    private AuthUserInfo currentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserInfo authUserInfo) {
            return authUserInfo;
        }
        return null;
    }

    private BnplUser findUserForAlert(UUID userPublicId) {
        return userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void validateReceivablePhone(BnplUser user) {
        if (!hasReceivablePhone(user)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림을 발송할 휴대폰 번호가 없습니다.");
        }
    }

    private boolean hasReceivablePhone(BnplUser user) {
        return user.getPhone() != null && !user.getPhone().isBlank();
    }

    // channel 문자열을 enum으로 변환하고 유효하지 않으면 400 오류를 반환한다.
    private NotificationChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림 채널은 필수입니다.");
        }
        try {
            return NotificationChannel.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 알림 채널입니다. KAKAO 또는 SMS 중 선택해주세요.");
        }
    }

    private OverdueType parseOverdueType(String overdueType) {
        String normalized = overdueType == null ? "ALL" : overdueType.trim().toUpperCase();
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return null;
        }
        try {
            return OverdueType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 연체 유형입니다. ALL, INTEREST, PRINCIPAL 중 선택해주세요.");
        }
    }

    // 커스텀 메시지가 없을 때 사용할 채널·유형별 기본 알림 템플릿을 반환한다.
    private String resolveAlertMessage(String customMessage, NotificationChannel channel, String alertType) {
        if (customMessage != null && !customMessage.isBlank()) {
            return customMessage.trim();
        }
        return "REPAYMENT".equals(alertType)
                ? buildDefaultRepaymentMessage(channel)
                : buildDefaultOverdueMessage(channel);
    }

    private String buildDefaultRepaymentMessage(NotificationChannel channel) {
        return "[농협은행 BNPL] 이번 달 상환 예정일이 다가왔습니다. 기한 내 상환 부탁드립니다.";
    }

    private String buildDefaultOverdueMessage(NotificationChannel channel) {
        return "[농협은행 BNPL] 미납 대금이 발생하였습니다. 빠른 시일 내 납부해 주시기 바랍니다.";
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private LocalDate normalizeStartDate(LocalDate startDate) {
        return startDate == null ? MIN_FILTER_DATE : startDate;
    }

    private LocalDate normalizeEndDate(LocalDate endDate) {
        return endDate == null ? MAX_FILTER_DATE : endDate;
    }
}
