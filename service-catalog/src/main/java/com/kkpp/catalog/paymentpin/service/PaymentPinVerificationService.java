package com.kkpp.catalog.paymentpin.service;

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
            log.info(
                    "중복 결제 PIN 검증 완료 이벤트를 무시합니다. eventId={}, verificationId={}, userPublicId={}",
                    event.eventId(),
                    event.verificationId(),
                    event.userPublicId()
            );
            return;
        }

        PaymentPinVerification verification = paymentPinVerificationRepository.save(PaymentPinVerification.from(event));
        log.info(
                "결제 PIN 검증 완료 정보를 저장했습니다. eventId={}, verificationId={}, userPublicId={}, expiresAt={}",
                verification.getEventId(),
                verification.getVerificationId(),
                verification.getUserPublicId(),
                verification.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public boolean exists(PaymentPinVerifiedEvent event) {
        return paymentPinVerificationRepository.existsByEventIdOrVerificationId(event.eventId(), event.verificationId());
    }

    @Transactional
    public void consumeForCheckout(UUID userPublicId, UUID verificationId, UUID paymentRequestPublicId) {
        if (verificationId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 ID가 필요합니다.");
        }

        PaymentPinVerification verification = paymentPinVerificationRepository
                .findByVerificationIdForUpdate(verificationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "결제 PIN 검증 정보가 아직 반영되지 않았습니다. 잠시 후 다시 시도해 주세요."
                ));

        if (!verification.isOwnedBy(userPublicId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 사용자가 일치하지 않습니다.");
        }
        if (verification.isExpired(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 유효 시간이 만료되었습니다.");
        }
        if (!verification.isVerified()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 사용된 결제 PIN 검증 ID입니다.");
        }

        verification.markUsed(paymentRequestPublicId, LocalDateTime.now(ZoneOffset.UTC));
        log.info(
                "결제 PIN 검증 정보를 외상 결제 요청에 사용 처리했습니다. verificationId={}, userPublicId={}, paymentRequestPublicId={}",
                verificationId,
                userPublicId,
                paymentRequestPublicId
        );
    }
}
