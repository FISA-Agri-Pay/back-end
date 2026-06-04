package com.kkpp.core.credithistory.controller;

import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.core.credithistory.dto.CreditRepaymentHistoryResponse;
import com.kkpp.core.credithistory.dto.CreditUsageHistoryResponse;
import com.kkpp.core.credithistory.service.CreditHistoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "외상 조회")
@RestController
@RequestMapping("/api/core/credit-history")
@RequiredArgsConstructor
public class CreditHistoryController {

    private final CreditHistoryQueryService creditHistoryQueryService;

    @Operation(
            summary = "외상 이용 내역 조회",
            description = """
                    인증 사용자 기준으로 외상 이용 내역을 최신순으로 조회합니다.
                    금액은 화면 표시 기준이며, 구매성 외상 이용은 음수로 응답합니다.
                    내역이 없으면 빈 배열을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "외상 이용 내역 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = CreditUsageHistoryResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "data": [
                                        {
                                          "historyPublicId": "11111111-1111-4111-8111-111111111111",
                                          "usedAt": "2026-05-11T10:00:00",
                                          "title": "드론 방제 서비스 (1,000평)",
                                          "amount": -100000.00,
                                          "usageType": "PURCHASE",
                                          "orderStatus": "CONFIRMED",
                                          "deliveryStatus": "SHIPPING",
                                          "displayStatus": "배송중"
                                        }
                                      ],
                                      "message": "외상 이용 내역을 조회했습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            )
    })
    @GetMapping("/usages")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<CreditUsageHistoryResponse>> getMyUsageHistories(
            @Parameter(hidden = true) @AuthUser AuthUserInfo authUser
    ) {
        List<CreditUsageHistoryResponse> response = creditHistoryQueryService.getMyUsageHistories(authUser.userId());
        return ApiResponse.success(response, "외상 이용 내역을 조회했습니다.");
    }

    @Operation(
            summary = "상환 및 납부 내역 조회",
            description = """
                    인증 사용자 기준으로 이자/원금 상환 내역을 최신순으로 조회합니다.
                    금액은 화면 표시 기준이며, 상환성 거래는 음수로 응답합니다.
                    지갑이나 내역이 없으면 빈 배열을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "상환 및 납부 내역 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = CreditRepaymentHistoryResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "data": [
                                        {
                                          "transactionPublicId": "22222222-2222-4222-8222-222222222222",
                                          "transactedAt": "2026-05-11T10:00:00",
                                          "title": "4월 이자 상환",
                                          "transactionType": "INTEREST_PAYMENT",
                                          "amount": -100000.00
                                        }
                                      ],
                                      "message": "상환 및 납부 내역을 조회했습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            )
    })
    @GetMapping("/repayments")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<CreditRepaymentHistoryResponse>> getMyRepaymentHistories(
            @Parameter(hidden = true) @AuthUser AuthUserInfo authUser
    ) {
        List<CreditRepaymentHistoryResponse> response = creditHistoryQueryService
                .getMyRepaymentHistories(authUser.userId());
        return ApiResponse.success(response, "상환 및 납부 내역을 조회했습니다.");
    }
}
