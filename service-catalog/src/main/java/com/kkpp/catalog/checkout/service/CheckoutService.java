package com.kkpp.catalog.checkout.service;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.request.DeliveryAddressRequest;
import com.kkpp.catalog.checkout.dto.response.CheckoutRequestResponse;
import com.kkpp.catalog.checkout.event.CreditPaymentEventProducer;
import com.kkpp.catalog.checkout.repository.BnplPaymentRequestRepository;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
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

    private final CartItemRepository cartItemRepository;
    private final BnplPaymentRequestRepository bnplPaymentRequestRepository;
    private final CreditPaymentEventProducer creditPaymentEventProducer;
    private final TransactionTemplate transactionTemplate;

    public CheckoutRequestResponse createCheckoutRequest(UUID userPublicId, CreateCheckoutRequest request) {
        validateRequest(userPublicId, request);
        UUID paymentRequestPublicId = paymentRequestPublicId(userPublicId, request.idempotencyKey());
        try {
            return transactionTemplate.execute(status -> createCheckoutRequestInTransaction(
                    userPublicId,
                    paymentRequestPublicId,
                    request
            ));
        } catch (DataIntegrityViolationException exception) {
            return recoverIdempotentPaymentRequest(userPublicId, paymentRequestPublicId, exception);
        }
    }

    private CheckoutRequestResponse createCheckoutRequestInTransaction(
            UUID userPublicId,
            UUID paymentRequestPublicId,
            CreateCheckoutRequest request
    ) {
        log.info(
                "외상 결제 요청 생성을 시작합니다. userPublicId={}, cartItemIds={}, paymentMethod={}, idempotencyKey={}",
                userPublicId,
                request.cartItemIds(),
                request.paymentMethod(),
                request.idempotencyKey()
        );
        validatePaymentMethod(request.paymentMethod());

        BnplPaymentRequest existingRequest = bnplPaymentRequestRepository
                .findByPublicIdAndUserPublicId(paymentRequestPublicId, userPublicId)
                .orElse(null);
        if (existingRequest != null) {
            log.info(
                    "멱등 외상 결제 요청을 재사용합니다. userPublicId={}, paymentRequestPublicId={}, idempotencyKey={}, status={}",
                    userPublicId,
                    existingRequest.getPublicId(),
                    request.idempotencyKey(),
                    existingRequest.getRequestStatus()
            );
            return CheckoutRequestResponse.from(existingRequest, orderPublicId(existingRequest.getPublicId()));
        }

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

            log.warn(
                    "결제 요청 장바구니 항목이 올바르지 않습니다. userPublicId={}, requestedCartItemIds={}, foundCartItemIds={}, missingCartItemIds={}",
                    userPublicId,
                    request.cartItemIds(),
                    foundCartItemIds,
                    missingCartItemIds
            );
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "결제할 장바구니 항목이 올바르지 않습니다. missingCartItemIds=" + missingCartItemIds
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
        log.info(
                "외상 결제 요청을 저장했습니다. userPublicId={}, paymentRequestPublicId={}, orderPublicId={}, totalAmount={}, status={}",
                userPublicId,
                paymentRequest.getPublicId(),
                orderPublicId,
                paymentRequest.getTotalAmount(),
                paymentRequest.getRequestStatus()
        );
        creditPaymentEventProducer.publish(toEvent(paymentRequest, orderPublicId, cartItems, request.deliveryAddress(), request.idempotencyKey()));
        return CheckoutRequestResponse.from(paymentRequest, orderPublicId);
    }

    private CheckoutRequestResponse recoverIdempotentPaymentRequest(
            UUID userPublicId,
            UUID paymentRequestPublicId,
            DataIntegrityViolationException exception
    ) {
        return bnplPaymentRequestRepository.findByPublicIdAndUserPublicId(paymentRequestPublicId, userPublicId)
                .map(existingRequest -> {
                    UUID orderPublicId = orderPublicId(existingRequest.getPublicId());
                    log.info(
                            "unique 제약 충돌 후 멱등 외상 결제 요청을 복구했습니다. userPublicId={}, paymentRequestPublicId={}, orderPublicId={}, status={}",
                            userPublicId,
                            existingRequest.getPublicId(),
                            orderPublicId,
                            existingRequest.getRequestStatus()
                    );
                    return CheckoutRequestResponse.from(existingRequest, orderPublicId);
                })
                .orElseThrow(() -> exception);
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수령인 이름이 비어 있습니다.");
        }
        if (isBlank(deliveryAddress.recipientPhone())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수령인 전화번호가 비어 있습니다.");
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

    private void validateCartItem(CartItem cartItem) {
        Product product = cartItem.getProduct();
        if (!"ON_SALE".equals(product.getStatus())) {
            log.warn(
                    "상품 상태 때문에 결제 요청을 막았습니다. cartItemId={}, productPublicId={}, productName={}, status={}",
                    cartItem.getId(),
                    product.getPublicId(),
                    product.getName(),
                    product.getStatus()
            );
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "판매 중인 상품만 결제할 수 있습니다. productName=" + product.getName()
                            + ", status=" + product.getStatus()
            );
        }
        if (product.getStockQuantity() < cartItem.getQuantity()) {
            log.warn(
                    "재고 부족 때문에 결제 요청을 막았습니다. cartItemId={}, productPublicId={}, productName={}, requestedQuantity={}, stockQuantity={}",
                    cartItem.getId(),
                    product.getPublicId(),
                    product.getName(),
                    cartItem.getQuantity(),
                    product.getStockQuantity()
            );
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "상품 재고가 부족합니다. productName=" + product.getName()
                            + ", requestedQuantity=" + cartItem.getQuantity()
                            + ", stockQuantity=" + product.getStockQuantity()
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
        // paymentRequestPublicId는 BNPL 결제요청, orderPublicId는 service-core가 생성할 주문 식별자다.
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
