package com.kkpp.catalog.checkout.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.VERIFICATION_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.cartItem;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.paymentRequest;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.request.DeliveryAddressRequest;
import com.kkpp.catalog.checkout.event.CreditPaymentEventProducer;
import com.kkpp.catalog.checkout.repository.BnplPaymentRequestRepository;
import com.kkpp.catalog.global.tracing.TracingSupport;
import com.kkpp.catalog.paymentpin.service.PaymentPinVerificationService;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private BnplPaymentRequestRepository bnplPaymentRequestRepository;

    @Mock
    private CreditPaymentEventProducer creditPaymentEventProducer;

    @Mock
    private PaymentPinVerificationService paymentPinVerificationService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TracingSupport tracingSupport;

    @Mock
    private Span span;

    @Mock
    private Scope scope;

    @Mock
    private TransactionStatus transactionStatus;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(
                cartItemRepository,
                bnplPaymentRequestRepository,
                creditPaymentEventProducer,
                paymentPinVerificationService,
                transactionTemplate,
                tracingSupport
        );
        lenient().when(tracingSupport.startSpan(org.mockito.ArgumentMatchers.anyString())).thenReturn(span);
        lenient().when(span.makeCurrent()).thenReturn(scope);
    }

    @Test
    void createCheckoutRequestStoresRequestConsumesPinAndPublishesEvent() {
        executeTransactionCallback();
        CartItem cartItem = cartItem(1L, USER_PUBLIC_ID, onSaleProduct(10), 2);
        when(bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID)))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findAllByUserPublicIdAndIdInWithProduct(USER_PUBLIC_ID, List.of(1L)))
                .thenReturn(List.of(cartItem));
        when(bnplPaymentRequestRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(BnplPaymentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest());

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.totalAmount()).isEqualByComparingTo("24000");
        verify(paymentPinVerificationService).consumeForCheckout(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                org.mockito.ArgumentMatchers.eq(VERIFICATION_ID),
                org.mockito.ArgumentMatchers.any(UUID.class)
        );
        verify(creditPaymentEventProducer).publish(org.mockito.ArgumentMatchers.any(CreditPaymentRequestedEvent.class));
    }

    @Test
    void createCheckoutRequestRejectsInvalidRequestBeforeTransaction() {
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(null, validRequest()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(),
                deliveryAddress(),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                null,
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                new DeliveryAddressRequest(" ", "010-0000-0000", "경북 안동", null, "36700"),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                new DeliveryAddressRequest("홍길동", " ", "경북 안동", null, "36700"),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                new DeliveryAddressRequest("홍길동", "010-0000-0000", " ", null, "36700"),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                new DeliveryAddressRequest("홍길동", "010-0000-0000", "경북 안동", null, " "),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                deliveryAddress(),
                " ",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                deliveryAddress(),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                " "
        ))).isInstanceOf(BusinessException.class);
        verify(transactionTemplate, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createCheckoutRequestRejectsUnsupportedPaymentMethodAndMissingVerificationId() {
        executeTransactionCallback();

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                deliveryAddress(),
                "CARD",
                VERIFICATION_ID,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, new CreateCheckoutRequest(
                List.of(1L),
                deliveryAddress(),
                "CREDIT_LIMIT",
                null,
                "idem-1"
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    void createCheckoutRequestRejectsDuplicateIdempotencyKey() {
        executeTransactionCallback();
        BnplPaymentRequest existing = paymentRequest(
                PAYMENT_REQUEST_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("24000"),
                cartItem(1L, USER_PUBLIC_ID, onSaleProduct(10), 2)
        );
        when(bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isInstanceOf(BusinessException.class);
        verify(creditPaymentEventProducer, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createCheckoutRequestRejectsMissingCartItemInvalidProductAndInsufficientStock() {
        executeTransactionCallback();
        when(bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID)))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findAllByUserPublicIdAndIdInWithProduct(USER_PUBLIC_ID, List.of(1L)))
                .thenReturn(List.of())
                .thenReturn(List.of(cartItem(1L, USER_PUBLIC_ID, product(1L, PRODUCT_PUBLIC_ID, category(1L, UUID.randomUUID(), "비료", "ACTIVE"), "HIDDEN", 10, new BigDecimal("12000")), 2)))
                .thenReturn(List.of(cartItem(1L, USER_PUBLIC_ID, onSaleProduct(1), 2)));

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createCheckoutRequestRecoversDataIntegrityConflictAsBusinessException() {
        when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        BnplPaymentRequest existing = paymentRequest(
                PAYMENT_REQUEST_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("24000"),
                cartItem(1L, USER_PUBLIC_ID, onSaleProduct(10), 2)
        );
        when(bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createCheckoutRequestRecordsRuntimeExceptionOnSpan() {
        RuntimeException failure = new RuntimeException("tx failed");
        when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

        assertThatThrownBy(() -> checkoutService.createCheckoutRequest(USER_PUBLIC_ID, validRequest()))
                .isSameAs(failure);
        verify(tracingSupport).recordException(span, failure);
    }

    @Test
    void getCheckoutRequestReturnsResponseOrThrows() {
        BnplPaymentRequest existing = paymentRequest(
                PAYMENT_REQUEST_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("24000"),
                cartItem(1L, USER_PUBLIC_ID, onSaleProduct(10), 2)
        );
        when(bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());

        var response = checkoutService.getCheckoutRequest(USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID);

        assertThat(response.paymentRequestPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);

        assertThatThrownBy(() -> checkoutService.getCheckoutRequest(USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> checkoutService.getCheckoutRequest(null, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
    }

    private void executeTransactionCallback() {
        when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });
    }

    private CreateCheckoutRequest validRequest() {
        return new CreateCheckoutRequest(
                List.of(1L),
                deliveryAddress(),
                "CREDIT_LIMIT",
                VERIFICATION_ID,
                "idem-1"
        );
    }

    private DeliveryAddressRequest deliveryAddress() {
        return new DeliveryAddressRequest("홍길동", "010-0000-0000", "경북 안동", "창고 앞", "36700");
    }

    private Product onSaleProduct(int stockQuantity) {
        return product(1L, PRODUCT_PUBLIC_ID, category(1L, UUID.randomUUID(), "비료", "ACTIVE"), "ON_SALE", stockQuantity, new BigDecimal("12000"));
    }
}
