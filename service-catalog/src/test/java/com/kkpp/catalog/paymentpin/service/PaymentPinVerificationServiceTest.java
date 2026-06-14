package com.kkpp.catalog.paymentpin.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.EVENT_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.VERIFICATION_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.expiredPaymentPinVerification;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.paymentPinVerification;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.paymentPinVerifiedEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.paymentpin.domain.PaymentPinVerification;
import com.kkpp.catalog.paymentpin.repository.PaymentPinVerificationRepository;
import com.kkpp.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentPinVerificationServiceTest {

    @Mock
    private PaymentPinVerificationRepository paymentPinVerificationRepository;

    private PaymentPinVerificationService paymentPinVerificationService;

    @BeforeEach
    void setUp() {
        paymentPinVerificationService = new PaymentPinVerificationService(paymentPinVerificationRepository);
    }

    @Test
    void storeSavesNewEventAndIgnoresDuplicate() {
        var event = paymentPinVerifiedEvent(EVENT_ID, VERIFICATION_ID, USER_PUBLIC_ID);
        when(paymentPinVerificationRepository.existsByEventIdOrVerificationId(EVENT_ID, VERIFICATION_ID))
                .thenReturn(false)
                .thenReturn(true);
        when(paymentPinVerificationRepository.save(org.mockito.ArgumentMatchers.any(PaymentPinVerification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentPinVerificationService.store(event);
        paymentPinVerificationService.store(event);

        verify(paymentPinVerificationRepository, times(1))
                .save(org.mockito.ArgumentMatchers.any(PaymentPinVerification.class));
    }

    @Test
    void existsDelegatesToRepository() {
        var event = paymentPinVerifiedEvent(EVENT_ID, VERIFICATION_ID, USER_PUBLIC_ID);
        when(paymentPinVerificationRepository.existsByEventIdOrVerificationId(EVENT_ID, VERIFICATION_ID)).thenReturn(true);

        assertThat(paymentPinVerificationService.exists(event)).isTrue();
    }

    @Test
    void consumeForCheckoutMarksVerificationAsUsed() {
        PaymentPinVerification verification = paymentPinVerification(VERIFICATION_ID, USER_PUBLIC_ID);
        when(paymentPinVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID))
                .thenReturn(Optional.of(verification));

        paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, VERIFICATION_ID, PAYMENT_REQUEST_PUBLIC_ID);

        assertThat(verification.getStatus()).isEqualTo(PaymentPinVerification.STATUS_USED);
        assertThat(verification.getPaymentRequestPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);
        assertThat(verification.getUsedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void consumeForCheckoutRejectsInvalidStates() {
        assertThatThrownBy(() -> paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, null, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);

        when(paymentPinVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, VERIFICATION_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);

        UUID otherUser = UUID.fromString("66666666-6666-4666-8666-666666666666");
        when(paymentPinVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID))
                .thenReturn(Optional.of(paymentPinVerification(VERIFICATION_ID, otherUser)))
                .thenReturn(Optional.of(expiredPaymentPinVerification(VERIFICATION_ID, USER_PUBLIC_ID)));

        assertThatThrownBy(() -> paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, VERIFICATION_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, VERIFICATION_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);

        PaymentPinVerification used = paymentPinVerification(VERIFICATION_ID, USER_PUBLIC_ID);
        used.markUsed(PAYMENT_REQUEST_PUBLIC_ID, LocalDateTime.now());
        when(paymentPinVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> paymentPinVerificationService.consumeForCheckout(USER_PUBLIC_ID, VERIFICATION_ID, PAYMENT_REQUEST_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
        verify(paymentPinVerificationRepository, never()).save(used);
    }
}
