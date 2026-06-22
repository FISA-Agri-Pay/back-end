package com.kkpp.catalog.cart.service;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.dto.request.AddCartItemRequest;
import com.kkpp.catalog.cart.dto.request.UpdateCartItemQuantityRequest;
import com.kkpp.catalog.cart.dto.response.CartItemResponse;
import com.kkpp.catalog.cart.dto.response.CartResponse;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userPublicId) {
        List<CartItem> cartItems = cartItemRepository.findAllByUserPublicIdWithProduct(userPublicId);
        CartResponse response = CartResponse.from(cartItems);
        log.atInfo()
                .addKeyValue("event", "catalog.cart.get.completed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("cartItems", LogMaskingUtils.summarizeCollection(cartItems))
                .addKeyValue("totalAmount", response.totalAmount())
                .addKeyValue("resultStatus", "SUCCESS")
                .log("장바구니 조회가 완료되었습니다.");
        return response;
    }

    @Transactional
    public CartItemResponse addCartItem(UUID userPublicId, AddCartItemRequest request) {
        Product product = productRepository.findByPublicIdWithCategory(request.productId())
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", "catalog.cart.item.add.failed")
                            .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                            .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(request.productId()))
                            .addKeyValue("quantity", request.quantity())
                            .addKeyValue("failureState", "PRODUCT_NOT_FOUND")
                            .addKeyValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .addKeyValue("errorMessage", ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .log("장바구니에 담을 상품을 찾을 수 없습니다.");
                    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다.");
                });

        CartItem cartItem = cartItemRepository.findByUserPublicIdAndProductPublicId(userPublicId, product.getPublicId())
                .map(existing -> {
                    validatePurchasable(product, calculateTotalQuantity(existing, request.quantity()));
                    existing.addQuantity(request.quantity());
                    return existing;
                })
                .orElseGet(() -> {
                    validatePurchasable(product, request.quantity());
                    return cartItemRepository.save(CartItem.create(userPublicId, product, request.quantity()));
                });

        log.atInfo()
                .addKeyValue("event", "catalog.cart.item.add.completed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("cartItemId", cartItem.getId())
                .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(product.getPublicId()))
                .addKeyValue("addedQuantity", request.quantity())
                .addKeyValue("currentQuantity", cartItem.getQuantity())
                .addKeyValue("resultStatus", "SUCCESS")
                .log("장바구니 항목 추가가 완료되었습니다.");
        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public CartItemResponse updateQuantity(UUID userPublicId, Long cartItemId, UpdateCartItemQuantityRequest request) {
        CartItem cartItem = getCartItem(userPublicId, cartItemId);
        validatePurchasable(cartItem.getProduct(), request.quantity());
        cartItem.changeQuantity(request.quantity());
        log.atInfo()
                .addKeyValue("event", "catalog.cart.item.update.completed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("cartItemId", cartItemId)
                .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(cartItem.getProduct().getPublicId()))
                .addKeyValue("quantity", request.quantity())
                .addKeyValue("resultStatus", "SUCCESS")
                .log("장바구니 항목 수량 변경이 완료되었습니다.");
        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public void deleteCartItem(UUID userPublicId, Long cartItemId) {
        CartItem cartItem = getCartItem(userPublicId, cartItemId);
        UUID productPublicId = cartItem.getProduct().getPublicId();
        cartItemRepository.delete(cartItem);
        log.atInfo()
                .addKeyValue("event", "catalog.cart.item.delete.completed")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("cartItemId", cartItemId)
                .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(productPublicId))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("장바구니 항목 삭제가 완료되었습니다.");
    }

    private CartItem getCartItem(UUID userPublicId, Long cartItemId) {
        return cartItemRepository.findByIdAndUserPublicId(cartItemId, userPublicId)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", "catalog.cart.item.lookup.failed")
                            .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                            .addKeyValue("cartItemId", cartItemId)
                            .addKeyValue("failureState", "CART_ITEM_NOT_FOUND")
                            .addKeyValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .addKeyValue("errorMessage", ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .log("장바구니 항목을 찾을 수 없습니다.");
                    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "장바구니 항목을 찾을 수 없습니다.");
                });
    }

    private long calculateTotalQuantity(CartItem cartItem, Integer quantity) {
        validatePositiveQuantity(quantity);
        return (long) cartItem.getQuantity() + quantity;
    }

    private void validatePurchasable(Product product, Integer quantity) {
        validatePositiveQuantity(quantity);
        validatePurchasable(product, quantity.longValue());
    }

    private void validatePurchasable(Product product, long quantity) {
        if (!"ON_SALE".equals(product.getStatus())) {
            log.atWarn()
                    .addKeyValue("event", "catalog.cart.item.validation.failed")
                    .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(product.getPublicId()))
                    .addKeyValue("productStatus", product.getStatus())
                    .addKeyValue("requestedQuantity", quantity)
                    .addKeyValue("failureState", "PRODUCT_NOT_ON_SALE")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .log("판매 중이 아닌 상품은 장바구니에 담거나 수량을 변경할 수 없습니다.");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "판매 중인 상품만 장바구니에 담을 수 있습니다.");
        }
        if (product.getStockQuantity() < quantity) {
            log.atWarn()
                    .addKeyValue("event", "catalog.cart.item.validation.failed")
                    .addKeyValue("productPublicId", LogMaskingUtils.maskIdentifier(product.getPublicId()))
                    .addKeyValue("requestedQuantity", quantity)
                    .addKeyValue("stockQuantity", product.getStockQuantity())
                    .addKeyValue("failureState", "INSUFFICIENT_STOCK")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .log("상품 재고가 부족해 장바구니 요청을 처리할 수 없습니다.");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "상품 재고가 부족합니다.");
        }
    }

    private void validatePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            log.atWarn()
                    .addKeyValue("event", "catalog.cart.item.validation.failed")
                    .addKeyValue("requestedQuantity", quantity)
                    .addKeyValue("failureState", "INVALID_QUANTITY")
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .log("장바구니 수량이 올바르지 않습니다.");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "장바구니 수량은 1개 이상이어야 합니다.");
        }
    }
}
