package com.kkpp.batch.overdue.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.overdue.domain.LoanOverdueLedger;
import com.kkpp.batch.overdue.repository.OverdueCreditLimitRepository;
import com.kkpp.batch.overdue.repository.OverdueInterestLedgerRepository;
import com.kkpp.batch.overdue.repository.OverdueLoanOverdueLedgerRepository;
import com.kkpp.batch.overdue.repository.OverduePrincipalRepaymentLedgerRepository;
import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueDetectionService {

    private final OverdueInterestLedgerRepository interestLedgerRepository;
    private final OverduePrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final OverdueCreditLimitRepository creditLimitRepository;
    private final OverdueLoanOverdueLedgerRepository loanOverdueLedgerRepository;

    @Transactional
    public Optional<InterestLedger> detectInterestOverdue(Long interestLedgerPrimaryKey, LocalDate today, LocalDateTime now) {
        validateRequiredInput(interestLedgerPrimaryKey, today, now, "이자 연체 감지");

        // Reader가 읽은 뒤 자동 상환 배치나 재실행으로 상태가 바뀔 수 있으므로 처리 직전에 다시 잠금 조회한다.
        InterestLedger interestLedger = interestLedgerRepository.findByIdForUpdate(interestLedgerPrimaryKey)
                .orElseThrow(() -> new IllegalStateException("연체 감지 대상 이자 원장을 찾을 수 없습니다. interestLedgerPrimaryKey="
                        + interestLedgerPrimaryKey));

        if (!interestLedger.isOverdueDetectionTarget(today)) {
            log.debug("이자 연체 감지 대상이 아니어서 건너뜁니다. interestLedgerPublicId={}, status={}, dueDate={}",
                    interestLedger.getPublicId(),
                    interestLedger.getStatus(),
                    interestLedger.getDueDate());
            return Optional.empty();
        }

        CreditLimit creditLimit = findCreditLimit(interestLedger.getCreditLimitPublicId());
        BigDecimal overdueAmount = interestLedger.getUnpaidAmount();
        int overdueDays = calculateOverdueDays(interestLedger.getDueDate(), today);

        interestLedger.markOverdue(now);
        upsertInterestOverdueLedger(interestLedger, creditLimit, overdueAmount, overdueDays);

        log.info("이자 연체 감지를 처리했습니다. interestLedgerPublicId={}, creditLimitPublicId={}, ledgerStatus={}",
                interestLedger.getPublicId(),
                creditLimit.getPublicId(),
                interestLedger.getStatus());
        return Optional.of(interestLedger);
    }

    @Transactional
    public Optional<PrincipalRepaymentLedger> detectPrincipalOverdue(
            Long principalRepaymentLedgerPrimaryKey,
            LocalDate today,
            LocalDateTime now
    ) {
        validateRequiredInput(principalRepaymentLedgerPrimaryKey, today, now, "원금 연체 감지");

        // 원금 원장도 처리 직전에 잠금 조회해 최신 amountPaid/status 기준으로 연체 여부를 다시 판단한다.
        PrincipalRepaymentLedger principalLedger = principalRepaymentLedgerRepository
                .findByIdForUpdate(principalRepaymentLedgerPrimaryKey)
                .orElseThrow(() -> new IllegalStateException("연체 감지 대상 원금 원장을 찾을 수 없습니다. principalLedgerPrimaryKey="
                        + principalRepaymentLedgerPrimaryKey));

        if (!principalLedger.isOverdueDetectionTarget(today)) {
            log.debug("원금 연체 감지 대상이 아니어서 건너뜁니다. principalRepaymentPublicId={}, status={}, dueDate={}",
                    principalLedger.getPublicId(),
                    principalLedger.getStatus(),
                    principalLedger.getDueDate());
            return Optional.empty();
        }

        CreditLimit creditLimit = findCreditLimit(principalLedger.getCreditLimitPublicId());
        BigDecimal overdueAmount = principalLedger.getUnpaidAmount();
        int overdueDays = calculateOverdueDays(principalLedger.getDueDate(), today);

        principalLedger.markOverdue(now);
        upsertPrincipalOverdueLedger(principalLedger, creditLimit, overdueAmount, overdueDays);

        log.info("원금 연체 감지를 처리했습니다. principalRepaymentPublicId={}, creditLimitPublicId={}, ledgerStatus={}",
                principalLedger.getPublicId(),
                creditLimit.getPublicId(),
                principalLedger.getStatus());
        return Optional.of(principalLedger);
    }

    private void upsertInterestOverdueLedger(
            InterestLedger interestLedger,
            CreditLimit creditLimit,
            BigDecimal overdueAmount,
            int overdueDays
    ) {
        loanOverdueLedgerRepository.findByInterestLedgerPublicIdAndResolvedAtIsNull(interestLedger.getPublicId())
                .ifPresentOrElse(
                        overdueLedger -> overdueLedger.updateActive(overdueAmount, overdueDays),
                        () -> loanOverdueLedgerRepository.save(LoanOverdueLedger.interestOverdue(
                                creditLimit.getUserPublicId(),
                                creditLimit.getPublicId(),
                                interestLedger.getPublicId(),
                                overdueAmount,
                                overdueDays
                        ))
                );
    }

    private void upsertPrincipalOverdueLedger(
            PrincipalRepaymentLedger principalLedger,
            CreditLimit creditLimit,
            BigDecimal overdueAmount,
            int overdueDays
    ) {
        loanOverdueLedgerRepository.findByPrincipalRepaymentPublicIdAndResolvedAtIsNull(principalLedger.getPublicId())
                .ifPresentOrElse(
                        overdueLedger -> overdueLedger.updateActive(overdueAmount, overdueDays),
                        () -> loanOverdueLedgerRepository.save(LoanOverdueLedger.principalOverdue(
                                creditLimit.getUserPublicId(),
                                creditLimit.getPublicId(),
                                principalLedger.getPublicId(),
                                overdueAmount,
                                overdueDays
                        ))
                );
    }

    private CreditLimit findCreditLimit(UUID creditLimitPublicId) {
        return creditLimitRepository.findByPublicId(creditLimitPublicId)
                .orElseThrow(() -> new IllegalStateException("연체 원장과 연결된 한도를 찾을 수 없습니다. creditLimitPublicId="
                        + creditLimitPublicId));
    }

    private int calculateOverdueDays(LocalDate dueDate, LocalDate today) {
        long overdueDays = ChronoUnit.DAYS.between(dueDate, today);
        if (overdueDays <= 0 || overdueDays > Integer.MAX_VALUE) {
            throw new IllegalStateException("연체 일수를 계산할 수 없습니다. dueDate=" + dueDate + ", targetDate=" + today);
        }
        return (int) overdueDays;
    }

    private void validateRequiredInput(Long ledgerId, LocalDate today, LocalDateTime now, String taskName) {
        if (ledgerId == null) {
            throw new IllegalArgumentException(taskName + " 대상 원장 id가 없습니다.");
        }
        if (today == null) {
            throw new IllegalArgumentException(taskName + " 기준일이 없습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException(taskName + " 처리 시각이 없습니다.");
        }
    }
}
