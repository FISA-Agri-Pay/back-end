package com.kkpp.core.payment.service;

import com.kkpp.core.payment.domain.CreditLimit;
import com.kkpp.core.payment.domain.CreditUsageLedger;
import com.kkpp.core.payment.domain.InterestLedger;
import com.kkpp.core.payment.domain.PaymentEventProcessLog;
import com.kkpp.core.payment.domain.PrincipalRepaymentLedger;
import com.kkpp.core.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.core.payment.exception.PaymentProcessingException;
import com.kkpp.core.payment.repository.CreditLimitRepository;
import com.kkpp.core.payment.repository.CreditUsageLedgerRepository;
import com.kkpp.core.payment.repository.InterestLedgerRepository;
import com.kkpp.core.payment.repository.PaymentEventProcessLogRepository;
import com.kkpp.core.payment.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.core.payment.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditPaymentProcessingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PURCHASE = "PURCHASE";
    private static final int MONTHS_IN_YEAR = 12;

    private final UserRepository userRepository;
    private final CreditLimitRepository creditLimitRepository;
    private final CreditUsageLedgerRepository creditUsageLedgerRepository;
    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final PaymentEventProcessLogRepository paymentEventProcessLogRepository;

    @Value("${core.payment.interest-due-days:30}")
    private int interestDueDays;

    @Transactional
    public void process(CreditPaymentRequestedMessage message) {
        validateMessage(message);

        if (paymentEventProcessLogRepository.existsByEventIdOrCheckoutRequestId(
                message.eventId(),
                message.checkoutRequestId()
        )) {
            log.info(
                    "이미 처리된 외상 결제 요청 이벤트입니다. eventId={}, checkoutRequestId={}, idempotencyKey={}",
                    message.eventId(),
                    message.checkoutRequestId(),
                    message.idempotencyKey()
            );
            return;
        }

        Long userId = resolveUserId(message);
        CreditLimit creditLimit = creditLimitRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, ACTIVE)
                .orElseThrow(() -> new PaymentProcessingException("활성 한도를 찾을 수 없습니다. userId=" + userId));

        LocalDate today = LocalDate.now();
        if (!creditLimit.isActive(today)) {
            throw new PaymentProcessingException(
                    "사용할 수 없는 한도 상태입니다. userId=" + userId
                            + ", creditLimitId=" + creditLimit.getId()
                            + ", status=" + creditLimit.getStatus()
                            + ", expiresAt=" + creditLimit.getExpiresAt()
            );
        }
        if (!creditLimit.canUse(message.totalAmount())) {
            throw new PaymentProcessingException(
                    "사용 가능 한도가 부족합니다. userId=" + userId
                            + ", creditLimitId=" + creditLimit.getId()
                            + ", availableAmount=" + creditLimit.availableAmount()
                            + ", requestedAmount=" + message.totalAmount()
            );
        }
        if (message.orderId() != null
                && creditUsageLedgerRepository.existsByOrderIdAndUsageType(message.orderId(), PURCHASE)) {
            log.info(
                    "주문에 대한 외상 사용 원장이 이미 존재합니다. eventId={}, orderId={}",
                    message.eventId(),
                    message.orderId()
            );
            paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                    message.eventId(),
                    message.checkoutRequestId(),
                    message.idempotencyKey()
            ));
            return;
        }

        LocalDateTime usedAt = Objects.requireNonNullElse(message.occurredAt(), LocalDateTime.now());
        creditLimit.use(message.totalAmount());
        creditUsageLedgerRepository.save(CreditUsageLedger.purchase(
                creditLimit.getId(),
                message.orderId(),
                message.totalAmount(),
                usedAt
        ));
        interestLedgerRepository.save(InterestLedger.upcoming(
                creditLimit.getId(),
                message.totalAmount(),
                today.plusDays(interestDueDays),
                calculateMonthlyInterest(message.totalAmount(), creditLimit.getInterestRate())
        ));
        principalRepaymentLedgerRepository.save(PrincipalRepaymentLedger.upcoming(
                creditLimit.getId(),
                creditLimit.getPrincipalDueDate(),
                message.totalAmount()
        ));
        paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                message.eventId(),
                message.checkoutRequestId(),
                message.idempotencyKey()
        ));

        log.info(
                "외상 결제 요청 이벤트 처리를 완료했습니다. eventId={}, checkoutRequestId={}, userId={}, creditLimitId={}, amount={}",
                message.eventId(),
                message.checkoutRequestId(),
                userId,
                creditLimit.getId(),
                message.totalAmount()
        );
    }

    private void validateMessage(CreditPaymentRequestedMessage message) {
        if (message == null) {
            throw new PaymentProcessingException("결제 요청 메시지가 비어 있습니다.");
        }
        if (message.eventId() == null || message.eventId().isBlank()) {
            throw new PaymentProcessingException("결제 요청 메시지 eventId가 비어 있습니다.");
        }
        if (message.checkoutRequestId() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 checkoutRequestId가 비어 있습니다.");
        }
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new PaymentProcessingException("결제 요청 메시지 idempotencyKey가 비어 있습니다.");
        }
        if (message.userId() == null && message.userPublicId() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 사용자 식별자가 비어 있습니다.");
        }
        if (message.totalAmount() == null || message.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("결제 요청 금액이 올바르지 않습니다. amount=" + message.totalAmount());
        }
    }

    private Long resolveUserId(CreditPaymentRequestedMessage message) {
        if (message.userId() != null) {
            return message.userId();
        }
        return userRepository.findByPublicId(message.userPublicId())
                .orElseThrow(() -> new PaymentProcessingException("사용자를 찾을 수 없습니다. userPublicId=" + message.userPublicId()))
                .getId();
    }

    private BigDecimal calculateMonthlyInterest(BigDecimal principal, BigDecimal annualInterestRate) {
        return principal.multiply(annualInterestRate)
                .divide(BigDecimal.valueOf(MONTHS_IN_YEAR), 2, RoundingMode.HALF_UP);
    }

    public boolean isDuplicateKeyFailure(Exception exception) {
        return exception instanceof DataIntegrityViolationException;
    }
}
