package com.kkpp.catalog.cart.service;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.dto.request.AddCartItemRequest;
import com.kkpp.catalog.cart.dto.request.UpdateCartItemQuantityRequest;
import com.kkpp.catalog.cart.dto.response.CartItemResponse;
import com.kkpp.catalog.cart.dto.response.CartResponse;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.catalog.user.domain.User;
import com.kkpp.catalog.user.repository.UserRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return CartResponse.from(cartItemRepository.findAllByUserIdWithProduct(userId));
    }

    @Transactional
    public CartItemResponse addCartItem(Long userId, AddCartItemRequest request) {
        User user = getUser(userId);
        Product product = productRepository.findByPublicIdWithCategory(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));

        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, product.getId())
                .map(existing -> {
                    validatePurchasable(product, calculateTotalQuantity(existing, request.quantity()));
                    existing.addQuantity(request.quantity());
                    return existing;
                })
                .orElseGet(() -> {
                    validatePurchasable(product, request.quantity());
                    return cartItemRepository.save(CartItem.create(user, product, request.quantity()));
                });

        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public CartItemResponse updateQuantity(Long userId, Long cartItemId, UpdateCartItemQuantityRequest request) {
        CartItem cartItem = getCartItem(userId, cartItemId);
        validatePurchasable(cartItem.getProduct(), request.quantity());
        cartItem.changeQuantity(request.quantity());
        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public void deleteCartItem(Long userId, Long cartItemId) {
        CartItem cartItem = getCartItem(userId, cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private CartItem getCartItem(Long userId, Long cartItemId) {
        return cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."));
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "판매 중인 상품만 장바구니에 담을 수 있습니다.");
        }
        if (product.getStockQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "상품 재고가 부족합니다.");
        }
    }

    private void validatePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "장바구니 수량은 1개 이상이어야 합니다.");
        }
    }
}
