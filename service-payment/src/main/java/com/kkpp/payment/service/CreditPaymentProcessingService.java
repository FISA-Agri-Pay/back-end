package com.kkpp.payment.service;

import com.kkpp.payment.domain.CreditLimit;
import com.kkpp.payment.domain.CreditUsageLedger;
import com.kkpp.payment.domain.Order;
import com.kkpp.payment.domain.PaymentEventProcessLog;
import com.kkpp.payment.domain.PrincipalRepaymentLedger;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.logging.LogMaskingUtils;
import com.kkpp.payment.global.logging.LoggingTimeUtils;
import com.kkpp.payment.global.logging.MonitoredEventLogging;
import com.kkpp.payment.repository.CreditLimitRepository;
import com.kkpp.payment.repository.CreditUsageLedgerRepository;
import com.kkpp.payment.repository.OrderRepository;
import com.kkpp.payment.repository.PaymentEventProcessLogRepository;
import com.kkpp.payment.repository.PrincipalRepaymentLedgerRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
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
    private static final Set<String> IDEMPOTENCY_UNIQUE_CONSTRAINTS = Set.of(
            "payment_event_process_logs_event_id_key",
            "payment_event_process_logs_payment_request_public_id_key",
            "uk_payment_event_process_logs_event_id",
            "uk_payment_event_process_logs_payment_request_public_id"
    );

    private final CreditLimitRepository creditLimitRepository;
    private final OrderRepository orderRepository;
    private final CreditUsageLedgerRepository creditUsageLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final PaymentEventProcessLogRepository paymentEventProcessLogRepository;

    /*
     * 외상 결제 요청 이벤트를 실제 DB 상태로 반영하는 핵심 처리입니다.
     * AOP가 메서드 전체의 시작/완료/실패와 custom span을 남기고, 이 메서드는 단계별 업무 상태를 남깁니다.
     */
    @Transactional
    @MonitoredEventLogging(
            event = "payment.credit-payment-request.db-apply",
            operationName = "외상 결제 요청 DB 반영",
            spanName = "service-payment.credit-payment-request.db-apply"
    )
    public void process(CreditPaymentRequestedMessage message) {
        long startedAtNanos = System.nanoTime();
        validateMessage(message);

        UUID eventId = parseEventId(message.eventId());
        UUID paymentRequestPublicId = message.paymentRequestPublicId();

        log.atInfo()
                .addKeyValue("event", "payment.credit-payment-request.db-apply.started")
                .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(message.orderPublicId()))
                .addKeyValue("userPublicId", LogMaskingUtils.maskUuid(message.userPublicId()))
                .addKeyValue("totalAmount", message.totalAmount())
                .addKeyValue("items", LogMaskingUtils.summarizeCollection(message.items()))
                .log("외상 결제 요청 DB 반영을 시작했습니다.");

        if (paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(eventId, paymentRequestPublicId)) {
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.db-apply.duplicated")
                    .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                    .addKeyValue("idempotencyKey", LogMaskingUtils.maskIdentifier(message.idempotencyKey()))
                    .addKeyValue("resultStatus", "DUPLICATE_IGNORED")
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .log("이미 처리된 외상 결제 요청 이벤트라 DB 반영을 건너뜁니다.");
            return;
        }

        Order order = orderRepository.findByPaymentRequestPublicId(paymentRequestPublicId)
                .orElse(null);
        if (order != null && creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(order.getPublicId(), PURCHASE)) {
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.db-apply.ledger-already-exists")
                    .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(order.getPublicId()))
                    .addKeyValue("usageType", PURCHASE)
                    .addKeyValue("resultStatus", "DUPLICATE_IGNORED")
                    .log("주문에 대한 외상 사용 원장이 이미 존재해 처리 로그만 저장합니다.");
            paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                    eventId,
                    paymentRequestPublicId,
                    message.idempotencyKey()
            ));
            return;
        }

        UUID orderPublicId = message.orderPublicId();
        UUID userPublicId = message.userPublicId();
        if (order == null) {
            order = orderRepository.save(Order.confirmed(
                    orderPublicId,
                    userPublicId,
                    paymentRequestPublicId,
                    message.totalAmount(),
                    message.deliveryAddress(),
                    message.items(),
                    Objects.requireNonNullElse(message.occurredAt(), LocalDateTime.now())
            ));
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.order.persisted")
                    .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(order.getPublicId()))
                    .addKeyValue("totalAmount", order.getTotalAmount())
                    .addKeyValue("orderStatus", order.getOrderStatus())
                    .addKeyValue("deliveryStatus", order.getDeliveryStatus())
                    .log("외상 결제 주문 정보를 저장했습니다.");
        }

        CreditLimit creditLimit = creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(userPublicId, ACTIVE)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", "payment.credit-payment-request.db-apply.failed")
                            .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                            .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                            .addKeyValue("userPublicId", LogMaskingUtils.maskUuid(userPublicId))
                            .addKeyValue("failureState", "ACTIVE_CREDIT_LIMIT_NOT_FOUND")
                            .addKeyValue("errorMessage", "활성 한도를 찾을 수 없습니다.")
                            .log("외상 결제 DB 반영 중 활성 한도를 찾지 못했습니다.");
                    return new PaymentProcessingException("활성 한도를 찾을 수 없습니다.");
                });

        LocalDate today = LocalDate.now();
        if (!creditLimit.isActive(today)) {
            log.atWarn()
                    .addKeyValue("event", "payment.credit-payment-request.db-apply.failed")
                    .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                    .addKeyValue("userPublicId", LogMaskingUtils.maskUuid(userPublicId))
                    .addKeyValue("creditLimitPublicId", LogMaskingUtils.maskUuid(creditLimit.getPublicId()))
                    .addKeyValue("creditLimitStatus", creditLimit.getStatus())
                    .addKeyValue("expiresAt", creditLimit.getExpiresAt())
                    .addKeyValue("failureState", "CREDIT_LIMIT_NOT_ACTIVE")
                    .addKeyValue("errorMessage", "사용할 수 없는 한도 상태입니다.")
                    .log("외상 결제 DB 반영 중 한도 상태가 유효하지 않습니다.");
            throw new PaymentProcessingException("사용할 수 없는 한도 상태입니다.");
        }
        if (!creditLimit.canUse(message.totalAmount())) {
            log.atWarn()
                    .addKeyValue("event", "payment.credit-payment-request.db-apply.failed")
                    .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                    .addKeyValue("userPublicId", LogMaskingUtils.maskUuid(userPublicId))
                    .addKeyValue("creditLimitPublicId", LogMaskingUtils.maskUuid(creditLimit.getPublicId()))
                    .addKeyValue("availableAmount", creditLimit.availableAmount())
                    .addKeyValue("requestedAmount", message.totalAmount())
                    .addKeyValue("failureState", "INSUFFICIENT_CREDIT_LIMIT")
                    .addKeyValue("errorMessage", "사용 가능한 한도가 부족합니다.")
                    .log("외상 결제 DB 반영 중 사용 가능한 한도가 부족합니다.");
            throw new PaymentProcessingException("사용 가능한 한도가 부족합니다.");
        }

        LocalDateTime usedAt = Objects.requireNonNullElse(message.occurredAt(), LocalDateTime.now());
        creditLimit.use(message.totalAmount());
        creditUsageLedgerRepository.save(CreditUsageLedger.purchase(
                creditLimit.getPublicId(),
                order.getPublicId(),
                paymentRequestPublicId,
                message.totalAmount(),
                usedAt
        ));
        principalRepaymentLedgerRepository.save(PrincipalRepaymentLedger.upcoming(
                creditLimit.getPublicId(),
                order.getPublicId(),
                paymentRequestPublicId,
                creditLimit.getPrincipalDueDate(),
                message.totalAmount()
        ));
        paymentEventProcessLogRepository.save(PaymentEventProcessLog.processed(
                eventId,
                paymentRequestPublicId,
                message.idempotencyKey()
        ));

        log.atInfo()
                .addKeyValue("event", "payment.credit-payment-request.db-apply.completed")
                .addKeyValue("eventId", LogMaskingUtils.maskUuid(eventId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(paymentRequestPublicId))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(order.getPublicId()))
                .addKeyValue("userPublicId", LogMaskingUtils.maskUuid(userPublicId))
                .addKeyValue("creditLimitPublicId", LogMaskingUtils.maskUuid(creditLimit.getPublicId()))
                .addKeyValue("usedAmount", message.totalAmount())
                .addKeyValue("availableAmountAfterUse", creditLimit.availableAmount())
                .addKeyValue("principalDueDate", creditLimit.getPrincipalDueDate())
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("외상 결제 요청 이벤트 DB 반영을 완료했습니다.");
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
        if (message.deliveryAddress() == null) {
            throw new PaymentProcessingException("결제 요청 메시지 deliveryAddress가 비어 있습니다.");
        }
        validateDeliveryAddress(message.deliveryAddress());
        if (message.totalAmount() == null || message.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("결제 요청 금액이 올바르지 않습니다.");
        }
    }

    private void validateDeliveryAddress(CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress) {
        if (isBlank(deliveryAddress.recipientName())) {
            throw new PaymentProcessingException("결제 요청 배송지 recipientName이 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.recipientPhone())) {
            throw new PaymentProcessingException("결제 요청 배송지 recipientPhone이 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.address())) {
            throw new PaymentProcessingException("결제 요청 배송지 address가 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.zipCode())) {
            throw new PaymentProcessingException("결제 요청 배송지 zipCode가 비어 있습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID parseEventId(String eventId) {
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new PaymentProcessingException("결제 요청 메시지 eventId가 UUID 형식이 아닙니다.", exception);
        }
    }

    public boolean isDuplicateKeyFailure(Exception exception) {
        if (!(exception instanceof DataIntegrityViolationException)) {
            return false;
        }
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return IDEMPOTENCY_UNIQUE_CONSTRAINTS.contains(extractConstraintName(sqlException));
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String extractConstraintName(SQLException sqlException) {
        try {
            Object serverErrorMessage = sqlException.getClass()
                    .getMethod("getServerErrorMessage")
                    .invoke(sqlException);
            if (serverErrorMessage == null) {
                return null;
            }
            Object constraint = serverErrorMessage.getClass()
                    .getMethod("getConstraint")
                    .invoke(serverErrorMessage);
            return constraint instanceof String constraintName ? constraintName : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
