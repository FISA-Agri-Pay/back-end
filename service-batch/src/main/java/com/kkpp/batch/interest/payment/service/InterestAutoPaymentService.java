package com.kkpp.batch.interest.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.kkpp.batch.interest.domain.CreditLimit;
import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.payment.domain.LoanOverdueLedger;
import com.kkpp.batch.interest.payment.domain.Wallet;
import com.kkpp.batch.interest.payment.domain.WalletTransaction;
import com.kkpp.batch.interest.payment.repository.InterestPaymentCreditLimitRepository;
import com.kkpp.batch.interest.payment.repository.InterestPaymentLedgerRepository;
import com.kkpp.batch.interest.payment.repository.LoanOverdueLedgerRepository;
import com.kkpp.batch.interest.payment.repository.WalletRepository;
import com.kkpp.batch.interest.payment.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestAutoPaymentService {

    private final InterestPaymentLedgerRepository interestPaymentLedgerRepository;
    private final InterestPaymentCreditLimitRepository interestPaymentCreditLimitRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LoanOverdueLedgerRepository loanOverdueLedgerRepository;

    @Transactional
    public Optional<InterestLedger> payAutomatically(Long interestLedgerId, LocalDate today, LocalDateTime now) {
        validateRequiredInput(interestLedgerId, today, now);

        // Reader가 읽은 원장 상태는 처리 시점에는 달라졌을 수 있다.
        // 따라서 서비스에서 원장을 다시 잠금 조회하고, 최신 amountPaid 기준으로 미납 금액을 재계산한다.
        InterestLedger interestLedger = interestPaymentLedgerRepository.findByIdForUpdate(interestLedgerId)
                .orElseThrow(() -> new IllegalStateException("자동 이자 상환 대상 원장을 찾을 수 없습니다. interestLedgerId="
                        + interestLedgerId));

        // 이미 PAID가 되었거나 납부일이 미래인 경우처럼 더 이상 처리할 필요가 없는 원장은 정상 스킵한다.
        if (!interestLedger.isPayableOn(today)) {
            log.debug("자동 이자 상환 대상이 아니어서 스킵합니다. interestLedgerId={}, status={}, dueDate={}",
                    interestLedger.getId(),
                    interestLedger.getStatus(),
                    interestLedger.getDueDate());
            return Optional.empty();
        }

        // 이자 원장은 한도 id만 가지고 있으므로, 사용자 지갑을 찾기 위해 한도에서 userId를 조회한다.
        CreditLimit creditLimit = interestPaymentCreditLimitRepository.findById(interestLedger.getCreditLimitId())
                .orElseThrow(() -> new IllegalStateException("이자 원장과 연결된 한도를 찾을 수 없습니다. creditLimitId="
                        + interestLedger.getCreditLimitId()));

        // 지갑 잔액 차감은 동시성에 민감하므로 지갑도 쓰기 잠금으로 조회한다.
        Wallet wallet = walletRepository.findByUserIdForUpdate(creditLimit.getUserId())
                .orElseThrow(() -> new IllegalStateException("자동 이자 상환 대상 사용자의 지갑을 찾을 수 없습니다. userId="
                        + creditLimit.getUserId()));

        // 잔액이 없으면 원장/지갑/거래 이력 모두 변경하지 않는다.
        if (!wallet.hasBalance()) {
            log.info("지갑 잔액이 없어 자동 이자 상환을 스킵합니다. interestLedgerId={}, creditLimitId={}, walletId={}",
                    interestLedger.getId(),
                    creditLimit.getId(),
                    wallet.getId());
            return Optional.empty();
        }

        // 실제 차감 금액은 지갑 잔액과 미납 이자 중 더 작은 값이다.
        // 이 계산 덕분에 지갑 잔액이 부족해도 가능한 만큼만 부분 상환할 수 있다.
        BigDecimal payableAmount = wallet.getBalance().min(interestLedger.getUnpaidAmount());
        if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        // 기존 OVERDUE 원장이 전액 납부되는 경우에만 연체 이력을 해소 처리해야 하므로 처리 전 상태를 보관한다.
        boolean wasOverdue = interestLedger.isOverdue();

        // 아래 세 작업은 같은 트랜잭션에서 처리된다.
        // 중간에 실패하면 지갑 차감, 원장 갱신, 거래 이력이 함께 롤백된다.
        wallet.withdraw(payableAmount, now);
        interestLedger.applyPayment(payableAmount, today, now);
        walletTransactionRepository.save(WalletTransaction.interestPayment(
                wallet.getId(),
                payableAmount,
                wallet.getBalance(),
                interestLedger.getId(),
                now
        ));
        log.info("자동 이자 상환을 처리했습니다. interestLedgerId={}, creditLimitId={}, walletId={}, ledgerStatus={}",
                interestLedger.getId(),
                creditLimit.getId(),
                wallet.getId(),
                interestLedger.getStatus());

        // 연체 이력은 삭제하지 않는다.
        // 전액 상환으로 미납이 사라진 경우 해소 시각과 해소 금액만 남겨 BSS 평가 이력을 보존한다.
        if (wasOverdue && interestLedger.isPaid()) {
            resolveInterestOverdues(interestLedger.getId(), now);
            log.info("자동 이자 상환으로 연체 이력을 해소했습니다. interestLedgerId={}", interestLedger.getId());
        }

        return Optional.of(interestLedger);
    }

    private void resolveInterestOverdues(Long interestLedgerId, LocalDateTime resolvedAt) {
        // 신규 연체 생성은 연체 감지 배치의 책임이다.
        // 이 배치는 이미 존재하는 연체 이력이 전액 상환으로 해소된 경우만 갱신한다.
        for (LoanOverdueLedger overdueLedger :
                loanOverdueLedgerRepository.findAllByInterestLedgerIdAndResolvedAtIsNull(interestLedgerId)) {
            overdueLedger.resolve(resolvedAt);
        }
    }

    private void validateRequiredInput(Long interestLedgerId, LocalDate today, LocalDateTime now) {
        if (interestLedgerId == null) {
            throw new IllegalArgumentException("자동 이자 상환 대상 원장 id가 없습니다.");
        }
        if (today == null) {
            throw new IllegalArgumentException("자동 이자 상환 기준일이 없습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("자동 이자 상환 처리 시각이 없습니다.");
        }
    }
}
