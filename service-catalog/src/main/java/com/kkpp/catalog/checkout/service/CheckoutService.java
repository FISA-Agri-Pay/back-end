package com.kkpp.catalog.checkout.service;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.checkout.domain.CheckoutRequest;
import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.request.DeliveryAddressRequest;
import com.kkpp.catalog.checkout.dto.response.CheckoutRequestResponse;
import com.kkpp.catalog.checkout.event.CreditPaymentEventProducer;
import com.kkpp.catalog.checkout.repository.CheckoutRequestRepository;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.user.domain.User;
import com.kkpp.catalog.user.repository.UserRepository;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String CREDIT_LIMIT_PAYMENT = "CREDIT_LIMIT";

    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final CheckoutRequestRepository checkoutRequestRepository;
    private final CreditPaymentEventProducer creditPaymentEventProducer;
    private final TransactionTemplate transactionTemplate;

    public CheckoutRequestResponse createCheckoutRequest(Long userId, CreateCheckoutRequest request) {
        try {
            return transactionTemplate.execute(status -> createCheckoutRequestInTransaction(userId, request));
        } catch (DataIntegrityViolationException exception) {
            return recoverIdempotentCheckoutRequest(userId, request.idempotencyKey(), exception);
        }
    }

    private CheckoutRequestResponse createCheckoutRequestInTransaction(Long userId, CreateCheckoutRequest request) {
        log.info(
                "Create checkout request received. userId={}, cartItemIds={}, paymentMethod={}, idempotencyKey={}",
                userId,
                request.cartItemIds(),
                request.paymentMethod(),
                request.idempotencyKey()
        );
        validatePaymentMethod(request.paymentMethod());
        User user = getUser(userId);

        CheckoutRequest existingRequest = checkoutRequestRepository
                .findByIdempotencyKeyAndUserId(request.idempotencyKey(), userId)
                .orElse(null);
        if (existingRequest != null) {
            log.info(
                    "Idempotent checkout request hit. userId={}, checkoutRequestId={}, idempotencyKey={}, status={}",
                    userId,
                    existingRequest.getPublicId(),
                    request.idempotencyKey(),
                    existingRequest.getStatus()
            );
            return CheckoutRequestResponse.from(existingRequest);
        }

        List<CartItem> cartItems = cartItemRepository.findAllByUserIdAndIdInWithProduct(userId, request.cartItemIds());
        if (cartItems.size() != request.cartItemIds().size()) {
            Set<Long> foundCartItemIds = new HashSet<>(cartItems.stream()
                    .map(CartItem::getId)
                    .toList());
            List<Long> missingCartItemIds = request.cartItemIds().stream()
                    .filter(cartItemId -> !foundCartItemIds.contains(cartItemId))
                    .toList();

            log.warn(
                    "Invalid checkout cart items. userId={}, requestedCartItemIds={}, foundCartItemIds={}, missingCartItemIds={}",
                    userId,
                    request.cartItemIds(),
                    foundCartItemIds,
                    missingCartItemIds
            );
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "결제할 장바구니 항목이 올바르지 않습니다. userId=" + userId
                            + ", missingCartItemIds=" + missingCartItemIds
                            + ", 요청한 장바구니 항목이 해당 사용자의 장바구니에 있는지 확인해 주세요."
            );
        }
        cartItems.forEach(this::validateCartItem);

        BigDecimal totalAmount = calculateTotalAmount(cartItems);
        CheckoutRequest checkoutRequest = checkoutRequestRepository.saveAndFlush(CheckoutRequest.create(
                user,
                totalAmount,
                request.paymentMethod(),
                request.idempotencyKey(),
                request.deliveryAddress().recipientName(),
                request.deliveryAddress().recipientPhone(),
                request.deliveryAddress().address(),
                request.deliveryAddress().addressDetail(),
                request.deliveryAddress().zipCode()
        ));

        log.info(
                "Checkout request saved. userId={}, checkoutRequestId={}, totalAmount={}, status={}",
                userId,
                checkoutRequest.getPublicId(),
                checkoutRequest.getTotalAmount(),
                checkoutRequest.getStatus()
        );
        creditPaymentEventProducer.publish(toEvent(checkoutRequest, user, cartItems, request.deliveryAddress()));
        return CheckoutRequestResponse.from(checkoutRequest);
    }

    private CheckoutRequestResponse recoverIdempotentCheckoutRequest(
            Long userId,
            String idempotencyKey,
            DataIntegrityViolationException exception
    ) {
        return checkoutRequestRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId)
                .map(existingRequest -> {
                    log.info(
                            "Idempotent checkout request recovered after unique constraint conflict. userId={}, checkoutRequestId={}, idempotencyKey={}, status={}",
                            userId,
                            existingRequest.getPublicId(),
                            idempotencyKey,
                            existingRequest.getStatus()
                    );
                    return CheckoutRequestResponse.from(existingRequest);
                })
                .orElseThrow(() -> exception);
    }

    @Transactional(readOnly = true)
    public CheckoutRequestResponse getCheckoutRequest(Long userId, UUID checkoutRequestId) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findByPublicIdAndUserId(checkoutRequestId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 요청을 찾을 수 없습니다."));
        return CheckoutRequestResponse.from(checkoutRequest);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
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
                    "Checkout blocked by product status. cartItemId={}, productId={}, productName={}, status={}",
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
                    "Checkout blocked by insufficient stock. cartItemId={}, productId={}, productName={}, requestedQuantity={}, stockQuantity={}",
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
            CheckoutRequest checkoutRequest,
            User user,
            List<CartItem> cartItems,
            DeliveryAddressRequest deliveryAddress
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
                checkoutRequest.getPublicId(),
                user.getPublicId(),
                checkoutRequest.getTotalAmount(),
                new CreditPaymentRequestedEvent.DeliveryAddress(
                        deliveryAddress.recipientName(),
                        deliveryAddress.recipientPhone(),
                        deliveryAddress.address(),
                        deliveryAddress.addressDetail(),
                        deliveryAddress.zipCode()
                ),
                items,
                checkoutRequest.getIdempotencyKey()
        );
    }
}
