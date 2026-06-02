package com.kkpp.payment.service;

import com.kkpp.payment.domain.CreditLimit;
import com.kkpp.payment.domain.CreditUsageLedger;
import com.kkpp.payment.domain.Order;
import com.kkpp.payment.domain.PaymentEventProcessLog;
import com.kkpp.payment.domain.PrincipalRepaymentLedger;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
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

    @Transactional
    public void process(CreditPaymentRequestedMessage message) {
        validateMessage(message);

        UUID eventId = parseEventId(message.eventId());
        UUID paymentRequestPublicId = message.paymentRequestPublicId();
        if (paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(eventId, paymentRequestPublicId)) {
            log.info(
                    "?대? 泥섎━???몄긽 寃곗젣 ?붿껌 ?대깽?몄엯?덈떎. eventId={}, paymentRequestPublicId={}, idempotencyKey={}",
                    eventId,
                    paymentRequestPublicId,
                    message.idempotencyKey()
            );
            return;
        }

        Order order = orderRepository.findByPaymentRequestPublicId(paymentRequestPublicId)
                .orElse(null);
        if (order != null && creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(order.getPublicId(), PURCHASE)) {
            log.info(
                    "二쇰Ц??????몄긽 ?ъ슜 ?먯옣???대? 議댁옱?⑸땲?? eventId={}, orderPublicId={}",
                    eventId,
                    order.getPublicId()
            );
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
        }
        CreditLimit creditLimit = creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(userPublicId, ACTIVE)
                .orElseThrow(() -> new PaymentProcessingException("?쒖꽦 ?쒕룄瑜?李얠쓣 ???놁뒿?덈떎. userPublicId=" + userPublicId));

        LocalDate today = LocalDate.now();
        if (!creditLimit.isActive(today)) {
            throw new PaymentProcessingException(
                    "?ъ슜?????녿뒗 ?쒕룄 ?곹깭?낅땲?? userPublicId=" + userPublicId
                            + ", creditLimitPublicId=" + creditLimit.getPublicId()
                            + ", status=" + creditLimit.getStatus()
                            + ", expiresAt=" + creditLimit.getExpiresAt()
            );
        }
        if (!creditLimit.canUse(message.totalAmount())) {
            throw new PaymentProcessingException(
                    "?ъ슜 媛???쒕룄媛 遺議깊빀?덈떎. userPublicId=" + userPublicId
                            + ", creditLimitPublicId=" + creditLimit.getPublicId()
                            + ", availableAmount=" + creditLimit.availableAmount()
                            + ", requestedAmount=" + message.totalAmount()
            );
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

        log.info(
                "?몄긽 寃곗젣 ?붿껌 ?대깽??泥섎━瑜??꾨즺?덉뒿?덈떎. eventId={}, paymentRequestPublicId={}, userPublicId={}, creditLimitPublicId={}, amount={}",
                eventId,
                paymentRequestPublicId,
                userPublicId,
                creditLimit.getPublicId(),
                message.totalAmount()
        );
    }

    private void validateMessage(CreditPaymentRequestedMessage message) {
        if (message == null) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.eventId() == null || message.eventId().isBlank()) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 eventId媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.paymentRequestPublicId() == null) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 paymentRequestPublicId媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 idempotencyKey媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.userPublicId() == null) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 userPublicId媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.orderPublicId() == null) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 orderPublicId媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (message.deliveryAddress() == null) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 deliveryAddress媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        validateDeliveryAddress(message.deliveryAddress());
        if (message.totalAmount() == null || message.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 湲덉븸???щ컮瑜댁? ?딆뒿?덈떎. amount=" + message.totalAmount());
        }
    }

    private void validateDeliveryAddress(CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress) {
        if (isBlank(deliveryAddress.recipientName())) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 諛곗넚吏 recipientName??鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (isBlank(deliveryAddress.recipientPhone())) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 諛곗넚吏 recipientPhone??鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (isBlank(deliveryAddress.address())) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 諛곗넚吏 address媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
        if (isBlank(deliveryAddress.zipCode())) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 諛곗넚吏 zipCode媛 鍮꾩뼱 ?덉뒿?덈떎.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID parseEventId(String eventId) {
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new PaymentProcessingException("寃곗젣 ?붿껌 硫붿떆吏 eventId媛 UUID ?뺤떇???꾨떃?덈떎. eventId=" + eventId, exception);
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

