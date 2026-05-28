package com.kkpp.batch.overdue.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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
    public Optional<InterestLedger> detectInterestOverdue(Long interestLedgerId, LocalDate today, LocalDateTime now) {
        validateRequiredInput(interestLedgerId, today, now, "이자 연체 감지");

        // Reader가 읽은 뒤 자동 상환 배치나 재실행으로 상태가 바뀔 수 있으므로 처리 직전에 다시 잠금 조회한다.
        InterestLedger interestLedger = interestLedgerRepository.findByIdForUpdate(interestLedgerId)
                .orElseThrow(() -> new IllegalStateException("연체 감지 대상 이자 원장을 찾을 수 없습니다. interestLedgerId="
                        + interestLedgerId));

        if (!interestLedger.isOverdueDetectionTarget(today)) {
            log.debug("이자 연체 감지 대상이 아니어서 건너뜁니다. interestLedgerId={}, status={}, dueDate={}",
                    interestLedger.getId(),
                    interestLedger.getStatus(),
                    interestLedger.getDueDate());
            return Optional.empty();
        }

        CreditLimit creditLimit = findCreditLimit(interestLedger.getCreditLimitId());
        BigDecimal overdueAmount = interestLedger.getUnpaidAmount();
        int overdueDays = calculateOverdueDays(interestLedger.getDueDate(), today);

        interestLedger.markOverdue(now);
        upsertInterestOverdueLedger(interestLedger, creditLimit, overdueAmount, overdueDays);

        log.info("이자 연체 감지를 처리했습니다. interestLedgerId={}, creditLimitId={}, ledgerStatus={}",
                interestLedger.getId(),
                creditLimit.getId(),
                interestLedger.getStatus());
        return Optional.of(interestLedger);
    }

    @Transactional
    public Optional<PrincipalRepaymentLedger> detectPrincipalOverdue(
            Long principalRepaymentLedgerId,
            LocalDate today,
            LocalDateTime now
    ) {
        validateRequiredInput(principalRepaymentLedgerId, today, now, "원금 연체 감지");

        // 원금 원장도 처리 직전에 잠금 조회해 최신 amountPaid/status 기준으로 연체 여부를 다시 판단한다.
        PrincipalRepaymentLedger principalLedger = principalRepaymentLedgerRepository
                .findByIdForUpdate(principalRepaymentLedgerId)
                .orElseThrow(() -> new IllegalStateException("연체 감지 대상 원금 원장을 찾을 수 없습니다. principalLedgerId="
                        + principalRepaymentLedgerId));

        if (!principalLedger.isOverdueDetectionTarget(today)) {
            log.debug("원금 연체 감지 대상이 아니어서 건너뜁니다. principalLedgerId={}, status={}, dueDate={}",
                    principalLedger.getId(),
                    principalLedger.getStatus(),
                    principalLedger.getDueDate());
            return Optional.empty();
        }

        CreditLimit creditLimit = findCreditLimit(principalLedger.getCreditLimitId());
        BigDecimal overdueAmount = principalLedger.getUnpaidAmount();
        int overdueDays = calculateOverdueDays(principalLedger.getDueDate(), today);

        principalLedger.markOverdue(now);
        upsertPrincipalOverdueLedger(principalLedger, creditLimit, overdueAmount, overdueDays);

        log.info("원금 연체 감지를 처리했습니다. principalLedgerId={}, creditLimitId={}, ledgerStatus={}",
                principalLedger.getId(),
                creditLimit.getId(),
                principalLedger.getStatus());
        return Optional.of(principalLedger);
    }

    private void upsertInterestOverdueLedger(
            InterestLedger interestLedger,
            CreditLimit creditLimit,
            BigDecimal overdueAmount,
            int overdueDays
    ) {
        loanOverdueLedgerRepository.findByInterestLedgerIdAndResolvedAtIsNull(interestLedger.getId())
                .ifPresentOrElse(
                        overdueLedger -> overdueLedger.updateActive(overdueAmount, overdueDays),
                        () -> loanOverdueLedgerRepository.save(LoanOverdueLedger.interestOverdue(
                                creditLimit.getUserId(),
                                creditLimit.getId(),
                                interestLedger.getId(),
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
        loanOverdueLedgerRepository.findByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(principalLedger.getId())
                .ifPresentOrElse(
                        overdueLedger -> overdueLedger.updateActive(overdueAmount, overdueDays),
                        () -> loanOverdueLedgerRepository.save(LoanOverdueLedger.principalOverdue(
                                creditLimit.getUserId(),
                                creditLimit.getId(),
                                principalLedger.getId(),
                                overdueAmount,
                                overdueDays
                        ))
                );
    }

    private CreditLimit findCreditLimit(Long creditLimitId) {
        return creditLimitRepository.findById(creditLimitId)
                .orElseThrow(() -> new IllegalStateException("연체 원장과 연결된 한도를 찾을 수 없습니다. creditLimitId="
                        + creditLimitId));
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
