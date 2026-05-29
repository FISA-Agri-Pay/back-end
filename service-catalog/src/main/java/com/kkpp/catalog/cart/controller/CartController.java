package com.kkpp.catalog.cart.controller;

import com.kkpp.catalog.cart.dto.request.AddCartItemRequest;
import com.kkpp.catalog.cart.dto.request.UpdateCartItemQuantityRequest;
import com.kkpp.catalog.cart.dto.response.CartItemResponse;
import com.kkpp.catalog.cart.dto.response.CartResponse;
import com.kkpp.catalog.cart.service.CartService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
@Tag(name = "장바구니 API", description = "농자재 상품을 장바구니에 담고 수량을 변경하거나 삭제합니다.")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니 상품 목록과 총 금액을 조회합니다. X-User-Public-Id 헤더가 필요합니다.")
    public ApiResponse<CartResponse> getCart(@RequestHeader("X-User-Public-Id") UUID userPublicId) {
        return ApiResponse.success(cartService.getCart(userPublicId));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "장바구니 담기", description = "상품을 장바구니에 담습니다. 이미 담긴 상품이면 기존 수량에 더합니다.")
    public ApiResponse<CartItemResponse> addCartItem(
            @RequestHeader("X-User-Public-Id") UUID userPublicId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return ApiResponse.success(cartService.addCartItem(userPublicId, request));
    }

    @PatchMapping("/items/{cartItemId}")
    @Operation(summary = "장바구니 수량 변경", description = "장바구니 항목의 수량을 변경합니다.")
    public ApiResponse<CartItemResponse> updateQuantity(
            @RequestHeader("X-User-Public-Id") UUID userPublicId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return ApiResponse.success(cartService.updateQuantity(userPublicId, cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(summary = "장바구니 삭제", description = "장바구니에서 특정 상품 항목을 삭제합니다.")
    public ApiResponse<Void> deleteCartItem(
            @RequestHeader("X-User-Public-Id") UUID userPublicId,
            @PathVariable Long cartItemId
    ) {
        cartService.deleteCartItem(userPublicId, cartItemId);
        return ApiResponse.success();
    }
}
