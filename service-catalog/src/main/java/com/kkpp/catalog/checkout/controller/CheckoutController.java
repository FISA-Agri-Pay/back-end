package com.kkpp.catalog.checkout.controller;

import com.kkpp.catalog.checkout.dto.request.CreateCheckoutRequest;
import com.kkpp.catalog.checkout.dto.response.CheckoutRequestResponse;
import com.kkpp.catalog.checkout.service.CheckoutService;
import com.kkpp.catalog.global.logging.MonitoredApiLogging;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checkout-requests")
@Tag(name = "외상 결제 요청 API", description = "AWS 채널계에서 BNPL 결제 요청을 생성하고 Kafka로 service-core에 전달합니다.")
public class CheckoutController {

    private final CheckoutService checkoutService;

    private UUID resolvePublicId(AuthUserInfo authUser) {
        if (authUser.publicId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.publicId();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // 외상 결제(BNPL) 요청 생성은 서비스 간 결제 흐름의 시작점이므로 AOP 공통 로그와 custom span을 함께 남깁니다.
    @MonitoredApiLogging(event = "catalog.bnpl.checkout-request.create.api", apiName = "외상 결제 요청 생성")
    @Operation(
            summary = "외상 결제 요청 생성",
            description = "장바구니 항목과 배송지를 기반으로 BNPL 결제 요청을 생성합니다. 실제 주문 확정과 원장 생성은 service-core가 Kafka 이벤트를 받아 처리합니다."
    )
    public ApiResponse<CheckoutRequestResponse> createCheckoutRequest(
            @AuthUser AuthUserInfo authUser,
            @Valid @RequestBody CreateCheckoutRequest request
    ) {
        return ApiResponse.success(checkoutService.createCheckoutRequest(resolvePublicId(authUser), request));
    }

    @GetMapping("/{paymentRequestPublicId}")
    @Operation(summary = "외상 결제 요청 상태 조회", description = "BNPL 결제 요청의 현재 상태를 조회합니다.")
    public ApiResponse<CheckoutRequestResponse> getCheckoutRequest(
            @AuthUser AuthUserInfo authUser,
            @PathVariable UUID paymentRequestPublicId
    ) {
        return ApiResponse.success(checkoutService.getCheckoutRequest(resolvePublicId(authUser), paymentRequestPublicId));
    }
}
