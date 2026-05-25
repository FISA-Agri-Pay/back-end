package com.kkpp.catalog.checkout.controller;

import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.response.CheckoutRequestResponse;
import com.kkpp.catalog.checkout.service.CheckoutService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checkout-requests")
@Tag(name = "외상 결제 요청 API", description = "AWS 채널계에서 결제 요청을 생성하고 Kafka로 온프레미스 결제계에 전달합니다.")
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "외상 결제 요청 생성",
            description = "장바구니 항목과 배송지를 기반으로 결제 요청을 생성합니다. 실제 한도 차감과 결제 승인은 온프레미스 결제계에서 처리하며, 이 API는 CreditPaymentRequested Kafka 메시지를 발행합니다."
    )
    public ApiResponse<CheckoutRequestResponse> createCheckoutRequest(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateCheckoutRequest request
    ) {
        return ApiResponse.success(checkoutService.createCheckoutRequest(userId, request));
    }

    @GetMapping("/{checkoutRequestId}")
    @Operation(summary = "외상 결제 요청 상태 조회", description = "결제 요청의 현재 상태를 조회합니다. 승인/거절 결과는 온프레미스 처리 후 Kafka 결과 이벤트로 반영됩니다.")
    public ApiResponse<CheckoutRequestResponse> getCheckoutRequest(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID checkoutRequestId
    ) {
        return ApiResponse.success(checkoutService.getCheckoutRequest(userId, checkoutRequestId));
    }
}
