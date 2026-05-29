package com.kkpp.core.payment.service;

import com.kkpp.core.payment.domain.CreditLimit;
import com.kkpp.core.payment.domain.CreditUsageLedger;
import com.kkpp.core.payment.domain.PaymentEventProcessLog;
import com.kkpp.core.payment.domain.PrincipalRepaymentLedger;
import com.kkpp.core.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.core.payment.exception.PaymentProcessingException;
import com.kkpp.core.payment.repository.CreditLimitRepository;
import com.kkpp.core.payment.repository.CreditUsageLedgerRepository;
import com.kkpp.core.payment.repository.PaymentEventProcessLogRepository;
import com.kkpp.core.payment.repository.PaymentUserRepository;
import com.kkpp.core.payment.repository.PrincipalRepaymentLedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditPaymentProcessingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PURCHASE = "PURCHASE";

    private final PaymentUserRepository userRepository;
    private final CreditLimitRepository creditLimitRepository;
    private final CreditUsageLedgerRepository creditUsageLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final PaymentEventProcessLogRepository paymentEventProcessLogRepository;

    @Transactional
    public void process(CreditPaymentRequestedMessage message) {
        validateMessage(message);

        if (paymentEventProcessLogRepository.existsByEventIdOrCheckoutRequestId(
                message.eventId(),
                message.checkoutRequestId()
        )) {
            log.info(
                    "Credit payment event already processed. eventId={}, checkoutRequestId={}, idempotencyKey={}",
                    message.eventId(),
                    message.checkoutRequestId(),
                    message.idempotencyKey()
            );
            return;
        }

        if (creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(message.orderPublicId(), PURCHASE)) {
            log.info(
                    "Credit usage ledger already exists for order. eventId={}, orderPublicId={}",
                    message.eventId(),
                    message.orderPublicId()
            );
            paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                    message.eventId(),
                    message.checkoutRequestId(),
                    message.idempotencyKey()
            ));
            return;
        }

        UUID userPublicId = resolveUserPublicId(message);
        CreditLimit creditLimit = creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(userPublicId, ACTIVE)
                .orElseThrow(() -> new PaymentProcessingException("Active credit limit not found. userPublicId=" + userPublicId));

        LocalDate today = LocalDate.now();
        if (!creditLimit.isActive(today)) {
            throw new PaymentProcessingException(
                    "Credit limit is not usable. userPublicId=" + userPublicId
                            + ", creditLimitPublicId=" + creditLimit.getPublicId()
                            + ", status=" + creditLimit.getStatus()
                            + ", expiresAt=" + creditLimit.getExpiresAt()
            );
        }
        if (!creditLimit.canUse(message.totalAmount())) {
            throw new PaymentProcessingException(
                    "Credit limit is insufficient. userPublicId=" + userPublicId
                            + ", creditLimitPublicId=" + creditLimit.getPublicId()
                            + ", availableAmount=" + creditLimit.availableAmount()
                            + ", requestedAmount=" + message.totalAmount()
            );
        }

        LocalDateTime usedAt = Objects.requireNonNullElse(message.occurredAt(), LocalDateTime.now());
        creditLimit.use(message.totalAmount());
        creditUsageLedgerRepository.save(CreditUsageLedger.purchase(
                creditLimit.getPublicId(),
                message.orderPublicId(),
                message.paymentRequestPublicId(),
                message.totalAmount(),
                usedAt
        ));
        principalRepaymentLedgerRepository.save(PrincipalRepaymentLedger.upcoming(
                creditLimit.getPublicId(),
                message.orderPublicId(),
                message.paymentRequestPublicId(),
                creditLimit.getPrincipalDueDate(),
                message.totalAmount()
        ));
        paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                message.eventId(),
                message.checkoutRequestId(),
                message.idempotencyKey()
        ));

        log.info(
                "Credit payment event processed. eventId={}, checkoutRequestId={}, userPublicId={}, creditLimitPublicId={}, amount={}",
                message.eventId(),
                message.checkoutRequestId(),
                userPublicId,
                creditLimit.getPublicId(),
                message.totalAmount()
        );
    }

    private void validateMessage(CreditPaymentRequestedMessage message) {
        if (message == null) {
            throw new PaymentProcessingException("Payment request message is empty.");
        }
        if (message.eventId() == null || message.eventId().isBlank()) {
            throw new PaymentProcessingException("Payment request eventId is empty.");
        }
        if (message.checkoutRequestId() == null) {
            throw new PaymentProcessingException("Payment request checkoutRequestId is empty.");
        }
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new PaymentProcessingException("Payment request idempotencyKey is empty.");
        }
        if (message.userId() == null && message.userPublicId() == null) {
            throw new PaymentProcessingException("Payment request user identifier is empty.");
        }
        if (message.orderPublicId() == null) {
            throw new PaymentProcessingException("Payment request orderPublicId is empty.");
        }
        if (message.totalAmount() == null || message.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Payment request amount is invalid. amount=" + message.totalAmount());
        }
    }

    private UUID resolveUserPublicId(CreditPaymentRequestedMessage message) {
        if (message.userPublicId() != null) {
            return message.userPublicId();
        }
        return userRepository.findById(message.userId())
                .orElseThrow(() -> new PaymentProcessingException("User not found. userId=" + message.userId()))
                .getPublicId();
    }

    public boolean isDuplicateKeyFailure(Exception exception) {
        return exception instanceof DataIntegrityViolationException;
    }
}
