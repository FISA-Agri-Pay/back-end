package com.kkpp.catalog.checkout.service;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.request.DeliveryAddressRequest;
import com.kkpp.catalog.checkout.dto.response.CheckoutRequestResponse;
import com.kkpp.catalog.checkout.event.CreditPaymentEventProducer;
import com.kkpp.catalog.checkout.repository.BnplPaymentRequestRepository;
import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.catalog.global.logging.LoggingTimeUtils;
import com.kkpp.catalog.global.tracing.TracingSupport;
import com.kkpp.catalog.paymentpin.service.PaymentPinVerificationService;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String CREDIT_LIMIT_PAYMENT = "CREDIT_LIMIT";

    /*
     * BNPL 외상 결제 요청 생성의 핵심 서비스입니다.
     * Controller AOP는 API 공통 흐름을, 이 클래스는 결제 요청 생성 단계별 상세 로그와 custom span을 담당합니다.
     */
    private final CartItemRepository cartItemRepository;
    private final BnplPaymentRequestRepository bnplPaymentRequestRepository;
    private final CreditPaymentEventProducer creditPaymentEventProducer;
    private final PaymentPinVerificationService paymentPinVerificationService;
    private final TransactionTemplate transactionTemplate;
    private final TracingSupport tracingSupport;

    public CheckoutRequestResponse createCheckoutRequest(UUID userPublicId, CreateCheckoutRequest request) {
        long startedAtNanos = System.nanoTime();
        Span span = tracingSupport.startSpan("service-catalog.bnpl.checkout-request.create");
        try (Scope ignored = span.makeCurrent()) {
            return createCheckoutRequestWithSpan(userPublicId, request, startedAtNanos, span);
        } catch (RuntimeException exception) {
            tracingSupport.recordException(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private CheckoutRequestResponse createCheckoutRequestWithSpan(
            UUID userPublicId,
            CreateCheckoutRequest request,
            long startedAtNanos,
            Span span
    ) {
        validateRequest(userPublicId, request);
        UUID paymentRequestPublicId = paymentRequestPublicId(userPublicId, request.idempotencyKey());
        span.setAttribute("kkpp.event", "catalog.bnpl.checkout-request.create");
        span.setAttribute("kkpp.user.public_id.masked", LogMaskingUtils.maskIdentifier(userPublicId));
        span.setAttribute("kkpp.payment_request.public_id.masked", LogMaskingUtils.maskIdentifier(paymentRequestPublicId));
        span.setAttribute("kkpp.payment.method", request.paymentMethod());
        span.setAttribute("kkpp.cart_item.count", request.cartItemIds().size());

        try {
            CheckoutRequestResponse response = transactionTemplate.execute(status -> createCheckoutRequestInTransaction(
                    userPublicId,
                    paymentRequestPublicId,
                    request,
                    startedAtNanos,
                    span
            ));
            span.setAttribute("kkpp.result", "SUCCESS");
            span.setAttribute("kkpp.duration_ms", LoggingTimeUtils.elapsedMillis(startedAtNanos));
            return response;
        } catch (DataIntegrityViolationException exception) {
            return recoverIdempotentPaymentRequest(userPublicId, paymentRequestPublicId, exception, startedAtNanos, span);
        }
    }

    private CheckoutRequestResponse createCheckoutRequestInTransaction(
            UUID userPublicId,
            UUID paymentRequestPublicId,
            CreateCheckoutRequest request,
            long startedAtNanos,
            Span span
    ) {
        // BNPL 요청 생성 시작 로그입니다. 배송지/전화번호/idempotencyKey 원문은 로그에 남기지 않습니다.
        log.atInfo()
                .addKeyValue("event", "catalog.bnpl.checkout-request.create.started")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequestPublicId))
                .addKeyValue("cartItems", LogMaskingUtils.summarizeCollection(request.cartItemIds()))
                .addKeyValue("paymentMethod", request.paymentMethod())
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(request.verificationId()))
                .addKeyValue("idempotencyKey", LogMaskingUtils.maskIdentifier(request.idempotencyKey()))
                .log("외상 결제 요청 생성을 시작합니다.");

        validatePaymentMethod(request.paymentMethod());
        validatePaymentPinVerificationId(request);

        BnplPaymentRequest existingRequest = bnplPaymentRequestRepository
                .findByPublicIdAndUserPublicId(paymentRequestPublicId, userPublicId)
                .orElse(null);
        if (existingRequest != null) {
            log.atWarn()
                    .addKeyValue("event", "catalog.bnpl.checkout-request.create.failed")
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(existingRequest.getPublicId()))
                    .addKeyValue("idempotencyKey", LogMaskingUtils.maskIdentifier(request.idempotencyKey()))
                    .addKeyValue("requestStatus", existingRequest.getRequestStatus())
                    .addKeyValue("failureState", "IDEMPOTENCY_KEY_ALREADY_USED")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .log("이미 사용된 멱등성 키로 외상 결제 요청이 다시 들어왔습니다.");
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "이미 사용된 idempotencyKey입니다. 새 결제 요청에는 새로운 idempotencyKey를 사용해 주세요."
            );
        }

        paymentPinVerificationService.consumeForCheckout(
                userPublicId,
                request.verificationId(),
                paymentRequestPublicId
        );

        List<CartItem> cartItems = cartItemRepository.findAllByUserPublicIdAndIdInWithProduct(
                userPublicId,
                request.cartItemIds()
        );
        if (cartItems.size() != request.cartItemIds().size()) {
            Set<Long> foundCartItemIds = new HashSet<>(cartItems.stream()
                    .map(CartItem::getId)
                    .toList());
            List<Long> missingCartItemIds = request.cartItemIds().stream()
                    .filter(cartItemId -> !foundCartItemIds.contains(cartItemId))
                    .toList();

            log.atWarn()
                    .addKeyValue("event", "catalog.bnpl.checkout-request.create.failed")
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequestPublicId))
                    .addKeyValue("requestedCartItems", LogMaskingUtils.summarizeCollection(request.cartItemIds()))
                    .addKeyValue("foundCartItems", LogMaskingUtils.summarizeCollection(foundCartItemIds))
                    .addKeyValue("missingCartItems", LogMaskingUtils.summarizeCollection(missingCartItemIds))
                    .addKeyValue("failureState", "CART_ITEM_NOT_FOUND")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .log("결제 요청 장바구니 항목이 올바르지 않습니다.");
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "결제할 장바구니 항목이 올바르지 않습니다."
            );
        }
        cartItems.forEach(this::validateCartItem);

        BigDecimal totalAmount = calculateTotalAmount(cartItems);
        BnplPaymentRequest paymentRequest = bnplPaymentRequestRepository.saveAndFlush(BnplPaymentRequest.create(
                paymentRequestPublicId,
                userPublicId,
                totalAmount,
                cartItems
        ));

        UUID orderPublicId = orderPublicId(paymentRequest.getPublicId());
        span.setAttribute("kkpp.order.public_id.masked", LogMaskingUtils.maskIdentifier(orderPublicId));
        span.setAttribute("kkpp.total_amount", totalAmount.doubleValue());
        log.atInfo()
                .addKeyValue("event", "catalog.bnpl.checkout-request.persisted")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequest.getPublicId()))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(orderPublicId))
                .addKeyValue("totalAmount", paymentRequest.getTotalAmount())
                .addKeyValue("requestStatus", paymentRequest.getRequestStatus())
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .log("외상 결제 요청을 저장했습니다.");

        creditPaymentEventProducer.publish(toEvent(paymentRequest, orderPublicId, cartItems, request.deliveryAddress(), request.idempotencyKey()));

        log.atInfo()
                .addKeyValue("event", "catalog.bnpl.checkout-request.create.completed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(paymentRequest.getPublicId()))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(orderPublicId))
                .addKeyValue("totalAmount", paymentRequest.getTotalAmount())
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("외상 결제 요청 생성과 이벤트 발행이 완료되었습니다.");
        return CheckoutRequestResponse.from(paymentRequest, orderPublicId);
    }

    private CheckoutRequestResponse recoverIdempotentPaymentRequest(
            UUID userPublicId,
            UUID paymentRequestPublicId,
            DataIntegrityViolationException exception,
            long startedAtNanos,
            Span span
    ) {
        BnplPaymentRequest existingRequest = bnplPaymentRequestRepository
                .findByPublicIdAndUserPublicId(paymentRequestPublicId, userPublicId)
                .orElseThrow(() -> exception);

        UUID orderPublicId = orderPublicId(existingRequest.getPublicId());
        span.setAttribute("kkpp.result", "FAILED");
        span.setAttribute("kkpp.failure_state", "IDEMPOTENCY_CONSTRAINT_CONFLICT");
        log.atWarn()
                .addKeyValue("event", "catalog.bnpl.checkout-request.create.failed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(existingRequest.getPublicId()))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(orderPublicId))
                .addKeyValue("requestStatus", existingRequest.getRequestStatus())
                .addKeyValue("failureState", "IDEMPOTENCY_CONSTRAINT_CONFLICT")
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .setCause(exception)
                .log("unique 제약 충돌 후 이미 생성된 외상 결제 요청을 확인했습니다.");
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "이미 사용된 idempotencyKey입니다. 새 결제 요청에는 새로운 idempotencyKey를 사용해 주세요."
        );
    }

    public CheckoutRequestResponse getCheckoutRequest(UUID userPublicId, UUID paymentRequestPublicId) {
        validateRequired(userPublicId, "userPublicId");
        validateRequired(paymentRequestPublicId, "paymentRequestPublicId");

        BnplPaymentRequest paymentRequest = bnplPaymentRequestRepository
                .findByPublicIdAndUserPublicId(paymentRequestPublicId, userPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 요청을 찾을 수 없습니다."));
        return CheckoutRequestResponse.from(paymentRequest, orderPublicId(paymentRequest.getPublicId()));
    }

    private void validateRequest(UUID userPublicId, CreateCheckoutRequest request) {
        validateRequired(userPublicId, "userPublicId");
        validateRequired(request, "request");
        if (request.cartItemIds() == null || request.cartItemIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제할 장바구니 항목이 비어 있습니다.");
        }
        validateDeliveryAddress(request.deliveryAddress());
        if (isBlank(request.paymentMethod())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 수단이 비어 있습니다.");
        }
        if (isBlank(request.idempotencyKey())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "멱등성 키가 비어 있습니다.");
        }
    }

    private void validateDeliveryAddress(DeliveryAddressRequest deliveryAddress) {
        validateRequired(deliveryAddress, "deliveryAddress");
        if (isBlank(deliveryAddress.recipientName())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수령자 이름이 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.recipientPhone())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수령자 전화번호가 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.address())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "배송 주소가 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.zipCode())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "배송 우편번호가 비어 있습니다.");
        }
    }

    private void validatePaymentMethod(String paymentMethod) {
        if (!CREDIT_LIMIT_PAYMENT.equals(paymentMethod)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "외상 한도 결제만 지원합니다.");
        }
    }

    private void validatePaymentPinVerificationId(CreateCheckoutRequest request) {
        if (CREDIT_LIMIT_PAYMENT.equals(request.paymentMethod()) && request.verificationId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제 PIN 검증 ID가 필요합니다.");
        }
    }

    private void validateCartItem(CartItem cartItem) {
        Product product = cartItem.getProduct();
        if (!"ON_SALE".equals(product.getStatus())) {
            log.atWarn()
                    .addKeyValue("event", "catalog.bnpl.checkout-request.create.failed")
                    .addKeyValue("cartItemId", cartItem.getId())
                    .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(product.getPublicId()))
                    .addKeyValue("productStatus", product.getStatus())
                    .addKeyValue("failureState", "PRODUCT_NOT_ON_SALE")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .log("상품 상태 때문에 외상 결제 요청을 막았습니다.");
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "판매 중인 상품만 결제할 수 있습니다."
            );
        }
        if (product.getStockQuantity() < cartItem.getQuantity()) {
            log.atWarn()
                    .addKeyValue("event", "catalog.bnpl.checkout-request.create.failed")
                    .addKeyValue("cartItemId", cartItem.getId())
                    .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(product.getPublicId()))
                    .addKeyValue("requestedQuantity", cartItem.getQuantity())
                    .addKeyValue("stockQuantity", product.getStockQuantity())
                    .addKeyValue("failureState", "INSUFFICIENT_STOCK")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .log("재고 부족 때문에 외상 결제 요청을 막았습니다.");
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "상품 재고가 부족합니다."
            );
        }
    }

    private BigDecimal calculateTotalAmount(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(cartItem -> cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CreditPaymentRequestedEvent toEvent(
            BnplPaymentRequest paymentRequest,
            UUID orderPublicId,
            List<CartItem> cartItems,
            DeliveryAddressRequest deliveryAddress,
            String idempotencyKey
    ) {
        List<CreditPaymentRequestedEvent.Item> items = cartItems.stream()
                .map(cartItem -> new CreditPaymentRequestedEvent.Item(
                        cartItem.getProduct().getPublicId(),
                        cartItem.getProduct().getName(),
                        cartItem.getProduct().getPrice(),
                        cartItem.getQuantity(),
                        cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                ))
                .toList();

        return new CreditPaymentRequestedEvent(
                UUID.randomUUID().toString(),
                "CreditPaymentRequested",
                LocalDateTime.now(),
                paymentRequest.getPublicId(),
                paymentRequest.getUserPublicId(),
                orderPublicId,
                paymentRequest.getTotalAmount(),
                new CreditPaymentRequestedEvent.DeliveryAddress(
                        deliveryAddress.recipientName(),
                        deliveryAddress.recipientPhone(),
                        deliveryAddress.address(),
                        deliveryAddress.addressDetail(),
                        deliveryAddress.zipCode()
                ),
                items,
                idempotencyKey
        );
    }

    private UUID paymentRequestPublicId(UUID userPublicId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes(("bnpl-payment-request:" + userPublicId + ":" + idempotencyKey)
                .getBytes(StandardCharsets.UTF_8));
    }

    private UUID orderPublicId(UUID paymentRequestPublicId) {
        // paymentRequestPublicId는 BNPL 결제요청, orderPublicId는 service-core가 생성할 주문 식별자입니다.
        return UUID.nameUUIDFromBytes(("core-order:" + paymentRequestPublicId).getBytes(StandardCharsets.UTF_8));
    }

    private void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + "는 필수입니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
