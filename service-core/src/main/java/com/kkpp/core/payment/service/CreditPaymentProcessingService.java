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

    private final CreditLimitRepository creditLimitRepository;
    private final CreditUsageLedgerRepository creditUsageLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final PaymentEventProcessLogRepository paymentEventProcessLogRepository;

    @Transactional
    public void process(CreditPaymentRequestedMessage message) {
        validateMessage(message);

        UUID eventId = parseEventId(message.eventId());
        UUID paymentRequestPublicId = message.paymentRequestPublicId();
        if (paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(eventId, paymentRequestPublicId)) {
            log.info(
                    "이미 처리된 외상 결제 요청 이벤트입니다. eventId={}, paymentRequestPublicId={}, idempotencyKey={}",
                    eventId,
                    paymentRequestPublicId,
                    message.idempotencyKey()
            );
            return;
        }

        if (creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(message.orderPublicId(), PURCHASE)) {
            log.info(
                    "주문에 대한 외상 사용 원장이 이미 존재합니다. eventId={}, orderPublicId={}",
                    eventId,
                    message.orderPublicId()
            );
            paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                    eventId,
                    paymentRequestPublicId,
                    message.idempotencyKey()
            ));
            return;
        }

        UUID userPublicId = message.userPublicId();
        CreditLimit creditLimit = creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(userPublicId, ACTIVE)
                .orElseThrow(() -> new PaymentProcessingException("활성 한도를 찾을 수 없습니다. userPublicId=" + userPublicId));

        LocalDate today = LocalDate.now();
        if (!creditLimit.isActive(today)) {
            throw new PaymentProcessingException(
                    "사용할 수 없는 한도 상태입니다. userPublicId=" + userPublicId
                            + ", creditLimitPublicId=" + creditLimit.getPublicId()
                            + ", status=" + creditLimit.getStatus()
                            + ", expiresAt=" + creditLimit.getExpiresAt()
            );
        }
        if (!creditLimit.canUse(message.totalAmount())) {
            throw new PaymentProcessingException(
                    "사용 가능 한도가 부족합니다. userPublicId=" + userPublicId
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
                paymentRequestPublicId,
                message.totalAmount(),
                usedAt
        ));
        principalRepaymentLedgerRepository.save(PrincipalRepaymentLedger.upcoming(
                creditLimit.getPublicId(),
                message.orderPublicId(),
                paymentRequestPublicId,
                creditLimit.getPrincipalDueDate(),
                message.totalAmount()
        ));
        paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                eventId,
                paymentRequestPublicId,
                message.idempotencyKey()
        ));

        log.info(
                "외상 결제 요청 이벤트 처리를 완료했습니다. eventId={}, paymentRequestPublicId={}, userPublicId={}, creditLimitPublicId={}, amount={}",
                eventId,
                paymentRequestPublicId,
                userPublicId,
                creditLimit.getPublicId(),
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
        if (message.paymentRequestPublicId() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 paymentRequestPublicId가 비어 있습니다.");
        }
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new PaymentProcessingException("결제 요청 메시지 idempotencyKey가 비어 있습니다.");
        }
        if (message.userPublicId() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 userPublicId가 비어 있습니다.");
        }
        if (message.orderPublicId() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 orderPublicId가 비어 있습니다.");
        }
        if (message.totalAmount() == null || message.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("결제 요청 금액이 올바르지 않습니다. amount=" + message.totalAmount());
        }
    }

    private UUID parseEventId(String eventId) {
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new PaymentProcessingException("결제 요청 메시지 eventId가 UUID 형식이 아닙니다. eventId=" + eventId, exception);
        }
    }

    public boolean isDuplicateKeyFailure(Exception exception) {
        return exception instanceof DataIntegrityViolationException;
    }
}
