package com.kkpp.core.wallet.controller;

import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.core.wallet.dto.WalletMeResponse;
import com.kkpp.core.wallet.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지갑")
@RestController
@RequestMapping("/api/core/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;

    @Operation(
            summary = "내 지갑 조회",
            description = """
                    인증 사용자 기준으로 내 지갑 화면에 필요한 정보를 조회합니다.
                    지갑 잔액, 입금 계좌, 이번 달 이자 예정 금액, 원금 잔액, 다음 상환 예정일,
                    상환 및 납부 내역을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 지갑 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = WalletMeResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "data": {
                                        "walletPublicId": "11111111-1111-4111-8111-111111111111",
                                        "depositBankName": "우리은행",
                                        "depositAccountNumber": "352-0000-0000-00",
                                        "balance": 200000.00,
                                        "nextRepaymentDate": "2026-06-11",
                                        "monthlyInterest": {
                                          "dueDate": "2026-06-11",
                                          "amount": 100000.00,
                                          "status": "UPCOMING"
                                        },
                                        "principal": {
                                          "dueDate": "2026-12-11",
                                          "remainingAmount": 3000000.00,
                                          "status": "UPCOMING"
                                        },
                                        "transactions": [
                                          {
                                            "transactionPublicId": "22222222-2222-4222-8222-222222222222",
                                            "transactionType": "INTEREST_PAYMENT",
                                            "title": "4월 이자 상환",
                                            "amount": -100000.00,
                                            "transactedAt": "2026-05-11T10:00:00"
                                          }
                                        ]
                                      },
                                      "message": "내 지갑 정보를 조회했습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "지갑 없음"
            )
    })
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<WalletMeResponse> getMyWallet(@Parameter(hidden = true) @AuthUser AuthUserInfo authUser) {
        WalletMeResponse response = walletQueryService.getMyWallet(authUser.userId());
        return ApiResponse.success(response, "내 지갑 정보를 조회했습니다.");
    }
}
