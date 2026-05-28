package com.kkpp.batch.principal.repayment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.kkpp.batch.interest.payment.domain.Wallet;
import com.kkpp.batch.interest.payment.domain.WalletTransaction;
import com.kkpp.batch.interest.payment.repository.WalletRepository;
import com.kkpp.batch.interest.payment.repository.WalletTransactionRepository;
import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import com.kkpp.batch.principal.repayment.domain.LoanOverdueLedger;
import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentCreditLimitRepository;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentLoanOverdueLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrincipalAutoPaymentService {

    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final PrincipalRepaymentCreditLimitRepository principalRepaymentCreditLimitRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PrincipalRepaymentLoanOverdueLedgerRepository loanOverdueLedgerRepository;

    @Transactional
    public Optional<PrincipalRepaymentLedger> payAutomatically(
            Long principalRepaymentLedgerId,
            LocalDate today,
            LocalDateTime now
    ) {
        validateRequiredInput(principalRepaymentLedgerId, today, now);

        // Reader가 읽은 뒤 원장 상태가 바뀌었을 수 있으므로 원장을 다시 잠금 조회한다.
        PrincipalRepaymentLedger principalLedger = principalRepaymentLedgerRepository
                .findByIdForUpdate(principalRepaymentLedgerId)
                .orElseThrow(() -> new IllegalStateException("자동 원금 상환 대상 원장을 찾을 수 없습니다. principalLedgerId="
                        + principalRepaymentLedgerId));

        if (!principalLedger.isPayableOn(today)) {
            log.debug("자동 원금 상환 대상이 아니어서 스킵합니다. principalLedgerId={}, status={}, dueDate={}",
                    principalLedger.getId(),
                    principalLedger.getStatus(),
                    principalLedger.getDueDate());
            return Optional.empty();
        }

        // usedAmount도 함께 갱신해야 하므로 한도도 비관적 락으로 조회한다.
        CreditLimit creditLimit = principalRepaymentCreditLimitRepository
                .findByIdForUpdate(principalLedger.getCreditLimitId())
                .orElseThrow(() -> new IllegalStateException("원금 원장과 연결된 한도를 찾을 수 없습니다. creditLimitId="
                        + principalLedger.getCreditLimitId()));

        Wallet wallet = walletRepository.findByUserIdForUpdate(creditLimit.getUserId())
                .orElseThrow(() -> new IllegalStateException("자동 원금 상환 대상 사용자의 지갑을 찾을 수 없습니다. userId="
                        + creditLimit.getUserId()));

        if (!wallet.hasBalance()) {
            log.info("지갑 잔액이 없어 자동 원금 상환을 스킵합니다. principalLedgerId={}, creditLimitId={}",
                    principalLedger.getId(),
                    creditLimit.getId());
            return Optional.empty();
        }

        // 지갑 차감액, 원장 납부 증가액, 한도 사용액 감소액은 반드시 같은 금액이어야 한다.
        BigDecimal payableAmount = min(wallet.getBalance(), principalLedger.getUnpaidAmount(), creditLimit.getUsedAmount());
        if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("상환 가능 금액이 없어 자동 원금 상환을 스킵합니다. principalLedgerId={}, creditLimitId={}",
                    principalLedger.getId(),
                    creditLimit.getId());
            return Optional.empty();
        }

        boolean wasOverdue = principalLedger.isOverdue();

        wallet.withdraw(payableAmount);
        principalLedger.applyPayment(payableAmount, today, now);
        creditLimit.decreaseUsedAmount(payableAmount);
        walletTransactionRepository.save(WalletTransaction.principalPayment(
                wallet.getId(),
                payableAmount,
                wallet.getBalance(),
                principalLedger.getId(),
                now
        ));

        log.info("자동 원금 상환을 처리했습니다. principalLedgerId={}, creditLimitId={}, ledgerStatus={}",
                principalLedger.getId(),
                creditLimit.getId(),
                principalLedger.getStatus());

        if (wasOverdue && principalLedger.isPaid()) {
            resolvePrincipalOverdues(principalLedger.getId(), now);
            log.info("자동 원금 상환으로 연체 이력을 해소했습니다. principalLedgerId={}", principalLedger.getId());
        }

        return Optional.of(principalLedger);
    }

    private void resolvePrincipalOverdues(Long principalRepaymentLedgerId, LocalDateTime resolvedAt) {
        // 신규 연체 생성은 연체 감지 배치 책임이다. 이 배치는 이미 존재하는 원금 연체만 해소 처리한다.
        for (LoanOverdueLedger overdueLedger :
                loanOverdueLedgerRepository.findAllByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(
                        principalRepaymentLedgerId)) {
            overdueLedger.resolve(resolvedAt);
        }
    }

    private BigDecimal min(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first.min(second).min(third);
    }

    private void validateRequiredInput(Long principalRepaymentLedgerId, LocalDate today, LocalDateTime now) {
        if (principalRepaymentLedgerId == null) {
            throw new IllegalArgumentException("자동 원금 상환 대상 원장 id가 없습니다.");
        }
        if (today == null) {
            throw new IllegalArgumentException("자동 원금 상환 기준일이 없습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("자동 원금 상환 처리 시각이 없습니다.");
        }
    }
}
