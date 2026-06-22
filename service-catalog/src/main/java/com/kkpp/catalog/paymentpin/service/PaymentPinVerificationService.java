package com.kkpp.catalog.paymentpin.service;

import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.catalog.paymentpin.domain.PaymentPinVerification;
import com.kkpp.catalog.paymentpin.event.PaymentPinVerifiedEvent;
import com.kkpp.catalog.paymentpin.repository.PaymentPinVerificationRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPinVerificationService {

    private final PaymentPinVerificationRepository paymentPinVerificationRepository;

    @Transactional
    public void store(PaymentPinVerifiedEvent event) {
        if (paymentPinVerificationRepository.existsByEventIdOrVerificationId(event.eventId(), event.verificationId())) {
            log.atInfo()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.duplicated")
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(event.userPublicId()))
                    .log("중복 결제 PIN 검증 완료 이벤트를 무시합니다.");
            return;
        }

        PaymentPinVerification verification = paymentPinVerificationRepository.save(PaymentPinVerification.from(event));
        log.atInfo()
                .addKeyValue("event", "catalog.payment-pin.verification.stored")
                .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(verification.getEventId()))
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(verification.getVerificationId()))
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(verification.getUserPublicId()))
                .addKeyValue("expiresAt", verification.getExpiresAt())
                .log("결제 PIN 검증 완료 정보를 저장했습니다.");
    }

    @Transactional(readOnly = true)
    public boolean exists(PaymentPinVerifiedEvent event) {
        return paymentPinVerificationRepository.existsByEventIdOrVerificationId(event.eventId(), event.verificationId());
    }

    @Transactional
    public void consumeForCheckout(UUID userPublicId, UUID verificationId, UUID paymentRequestPublicId) {
        if (verificationId == null) {
            logFailure(userPublicId, verificationId, paymentRequestPublicId, "VERIFICATION_ID_REQUIRED");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 ID가 필요합니다.");
        }

        PaymentPinVerification verification = paymentPinVerificationRepository
                .findByVerificationIdForUpdate(verificationId)
                .orElseThrow(() -> {
                    logFailure(userPublicId, verificationId, paymentRequestPublicId, "VERIFICATION_NOT_FOUND");
                    return new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "결제 PIN 검증 정보가 아직 반영되지 않았습니다. 잠시 후 다시 시도해 주세요."
                    );
                });

        if (!verification.isOwnedBy(userPublicId)) {
            logFailure(userPublicId, verificationId, paymentRequestPublicId, "USER_MISMATCH");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 사용자가 일치하지 않습니다.");
        }
        if (verification.isExpired(LocalDateTime.now(ZoneOffset.UTC))) {
            logFailure(userPublicId, verificationId, paymentRequestPublicId, "VERIFICATION_EXPIRED");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 유효 시간이 만료되었습니다.");
        }
        if (!verification.isVerified()) {
            logFailure(userPublicId, verificationId, paymentRequestPublicId, "VERIFICATION_ALREADY_USED");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 사용된 결제 PIN 검증 ID입니다.");
        }

        verification.markUsed(paymentRequestPublicId, LocalDateTime.now(ZoneOffset.UTC));
        log.atInfo()
                .addKeyValue("event", "catalog.payment-pin.verification.consumed")
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(verificationId))
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequestPublicId))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("결제 PIN 검증 정보를 외상 결제 요청에 사용 처리했습니다.");
    }

    private void logFailure(UUID userPublicId, UUID verificationId, UUID paymentRequestPublicId, String failureState) {
        log.atWarn()
                .addKeyValue("event", "catalog.payment-pin.verification.consume.failed")
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(verificationId))
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequestPublicId))
                .addKeyValue("failureState", failureState)
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                .log("결제 PIN 검증 정보를 외상 결제 요청에 사용할 수 없습니다.");
    }
}
