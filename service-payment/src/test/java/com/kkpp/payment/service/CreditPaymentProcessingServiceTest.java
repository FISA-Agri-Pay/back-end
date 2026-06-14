package com.kkpp.payment.service;

import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.EVENT_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.ORDER_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.activeCreditLimit;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.creditLimit;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.deliveryAddress;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.message;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.messageWith;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreditPaymentProcessingServiceTest {

    @Mock
    private CreditLimitRepository creditLimitRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CreditUsageLedgerRepository creditUsageLedgerRepository;

    @Mock
    private PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;

    @Mock
    private PaymentEventProcessLogRepository paymentEventProcessLogRepository;

    private CreditPaymentProcessingService service;

    @BeforeEach
    void setUp() {
        service = new CreditPaymentProcessingService(
                creditLimitRepository,
                orderRepository,
                creditUsageLedgerRepository,
                principalRepaymentLedgerRepository,
                paymentEventProcessLogRepository
        );
    }

    @Test
    void processCreatesOrderLedgersAndProcessLog() {
        CreditLimit creditLimit = activeCreditLimit();
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(false);
        when(orderRepository.findByPaymentRequestPublicId(PAYMENT_REQUEST_PUBLIC_ID)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(USER_PUBLIC_ID, "ACTIVE"))
                .thenReturn(Optional.of(creditLimit));

        service.process(message());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        ArgumentCaptor<CreditUsageLedger> usageLedgerCaptor = ArgumentCaptor.forClass(CreditUsageLedger.class);
        ArgumentCaptor<PrincipalRepaymentLedger> principalLedgerCaptor = ArgumentCaptor.forClass(PrincipalRepaymentLedger.class);
        ArgumentCaptor<PaymentEventProcessLog> logCaptor = ArgumentCaptor.forClass(PaymentEventProcessLog.class);
        verify(orderRepository).save(orderCaptor.capture());
        verify(creditUsageLedgerRepository).save(usageLedgerCaptor.capture());
        verify(principalRepaymentLedgerRepository).save(principalLedgerCaptor.capture());
        verify(paymentEventProcessLogRepository).save(logCaptor.capture());

        assertThat(orderCaptor.getValue().getPublicId()).isEqualTo(ORDER_PUBLIC_ID);
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("120000");
        assertThat(usageLedgerCaptor.getValue().getOrderPublicId()).isEqualTo(ORDER_PUBLIC_ID);
        assertThat(usageLedgerCaptor.getValue().getAmount()).isEqualByComparingTo("120000");
        assertThat(principalLedgerCaptor.getValue().getPrincipalAmount()).isEqualByComparingTo("120000");
        assertThat(logCaptor.getValue().getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void processSkipsAlreadyProcessedEvent() {
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(true);

        service.process(message());

        verify(orderRepository, never()).findByPaymentRequestPublicId(any(UUID.class));
        verify(creditUsageLedgerRepository, never()).save(any());
        verify(paymentEventProcessLogRepository, never()).save(any());
    }

    @Test
    void processWritesProcessLogOnlyWhenOrderLedgerAlreadyExists() {
        Order order = order();
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(false);
        when(orderRepository.findByPaymentRequestPublicId(PAYMENT_REQUEST_PUBLIC_ID)).thenReturn(Optional.of(order));
        when(creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(ORDER_PUBLIC_ID, "PURCHASE"))
                .thenReturn(true);

        service.process(message());

        verify(paymentEventProcessLogRepository).save(any(PaymentEventProcessLog.class));
        verify(creditLimitRepository, never()).findFirstByUserPublicIdAndStatusOrderByIdDesc(any(), any());
        verify(creditUsageLedgerRepository, never()).save(any());
        verify(principalRepaymentLedgerRepository, never()).save(any());
    }

    @Test
    void processUsesExistingOrderWhenLedgerDoesNotExist() {
        Order order = order();
        CreditLimit creditLimit = activeCreditLimit();
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(false);
        when(orderRepository.findByPaymentRequestPublicId(PAYMENT_REQUEST_PUBLIC_ID)).thenReturn(Optional.of(order));
        when(creditUsageLedgerRepository.existsByOrderPublicIdAndUsageType(ORDER_PUBLIC_ID, "PURCHASE"))
                .thenReturn(false);
        when(creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(USER_PUBLIC_ID, "ACTIVE"))
                .thenReturn(Optional.of(creditLimit));

        service.process(message());

        verify(orderRepository, never()).save(any(Order.class));
        verify(creditUsageLedgerRepository).save(any(CreditUsageLedger.class));
        verify(principalRepaymentLedgerRepository).save(any(PrincipalRepaymentLedger.class));
        verify(paymentEventProcessLogRepository).save(any(PaymentEventProcessLog.class));
    }

    @Test
    void processThrowsWhenActiveCreditLimitDoesNotExist() {
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(false);
        when(orderRepository.findByPaymentRequestPublicId(PAYMENT_REQUEST_PUBLIC_ID)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(USER_PUBLIC_ID, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(message()))
                .isInstanceOf(PaymentProcessingException.class);

        verify(creditUsageLedgerRepository, never()).save(any());
        verify(paymentEventProcessLogRepository, never()).save(any());
    }

    @Test
    void processThrowsWhenCreditLimitIsInactiveExpiredOrInsufficient() {
        assertPaymentFailure(creditLimit("SUSPENDED", new BigDecimal("500000"), BigDecimal.ZERO, LocalDate.now().plusMonths(1)));
        assertPaymentFailure(creditLimit("ACTIVE", new BigDecimal("500000"), BigDecimal.ZERO, LocalDate.now().minusDays(1)));
        assertPaymentFailure(creditLimit("ACTIVE", new BigDecimal("100000"), BigDecimal.ZERO, LocalDate.now().plusMonths(1)));
    }

    @Test
    void processRejectsInvalidMessageFields() {
        assertThatThrownBy(() -> service.process(null)).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(null, PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(" ", PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith("not-a-uuid", PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), null, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(new CreditPaymentRequestedMessage(
                EVENT_ID.toString(), "CREDIT_PAYMENT_REQUESTED", null, PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress(), java.util.List.of(), null
        ))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, null,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                null, BigDecimal.ONE, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, null))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, null, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ZERO, deliveryAddress()))).isInstanceOf(PaymentProcessingException.class);
        assertThatThrownBy(() -> service.process(new CreditPaymentRequestedMessage(
                EVENT_ID.toString(), "CREDIT_PAYMENT_REQUESTED", null, PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress(), java.util.List.of(), " "
        ))).isInstanceOf(PaymentProcessingException.class);
    }

    @Test
    void processRejectsInvalidDeliveryAddressFields() {
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                " ", "010-0000-0000", "경기도 안성시", null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                null, "010-0000-0000", "경기도 안성시", null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", " ", "경기도 안성시", null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", null, "경기도 안성시", null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", "010-0000-0000", " ", null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", "010-0000-0000", null, null, "17500"
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", "010-0000-0000", "경기도 안성시", null, " "
        ));
        assertInvalidDeliveryAddress(new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동", "010-0000-0000", "경기도 안성시", null, null
        ));
    }

    @Test
    void isDuplicateKeyFailureDetectsKnownPostgresConstraint() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new ConstraintSQLException("23505", "uk_payment_event_process_logs_event_id")
        );

        assertThat(service.isDuplicateKeyFailure(exception)).isTrue();
    }

    @Test
    void isDuplicateKeyFailureReturnsFalseForUnknownOrNonDuplicateErrors() {
        assertThat(service.isDuplicateKeyFailure(new RuntimeException("failed"))).isFalse();
        assertThat(service.isDuplicateKeyFailure(new DataIntegrityViolationException(
                "duplicate",
                new ConstraintSQLException("23505", "unknown_constraint")
        ))).isFalse();
        assertThat(service.isDuplicateKeyFailure(new DataIntegrityViolationException(
                "failed",
                new ConstraintSQLException("99999", "uk_payment_event_process_logs_event_id")
        ))).isFalse();
    }

    private void assertPaymentFailure(CreditLimit creditLimit) {
        when(paymentEventProcessLogRepository.existsByEventIdOrPaymentRequestPublicId(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .thenReturn(false);
        when(orderRepository.findByPaymentRequestPublicId(PAYMENT_REQUEST_PUBLIC_ID)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditLimitRepository.findFirstByUserPublicIdAndStatusOrderByIdDesc(USER_PUBLIC_ID, "ACTIVE"))
                .thenReturn(Optional.of(creditLimit));

        assertThatThrownBy(() -> service.process(message()))
                .isInstanceOf(PaymentProcessingException.class);
    }

    private void assertInvalidDeliveryAddress(CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress) {
        assertThatThrownBy(() -> service.process(messageWith(EVENT_ID.toString(), PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID,
                ORDER_PUBLIC_ID, BigDecimal.ONE, deliveryAddress)))
                .isInstanceOf(PaymentProcessingException.class);
    }

    public static final class ConstraintSQLException extends SQLException {

        private final transient ServerError serverErrorMessage;

        ConstraintSQLException(String sqlState, String constraintName) {
            super("constraint failed", sqlState);
            this.serverErrorMessage = new ServerError(constraintName);
        }

        public ServerError getServerErrorMessage() {
            return serverErrorMessage;
        }
    }

    public record ServerError(String constraint) {

        public String getConstraint() {
            return constraint;
        }
    }

}
