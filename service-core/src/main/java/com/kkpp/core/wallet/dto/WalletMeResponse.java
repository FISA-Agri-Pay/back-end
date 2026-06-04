package com.kkpp.core.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "내 지갑 조회 응답")
public record WalletMeResponse(
        @Schema(description = "지갑 publicId", example = "11111111-1111-4111-8111-111111111111")
        UUID walletPublicId,

        @Schema(description = "입금 은행명", example = "우리은행")
        String depositBankName,

        @Schema(description = "입금 계좌번호", example = "352-0000-0000-00")
        String depositAccountNumber,

        @Schema(description = "현재 입금 금액", example = "200000.00")
        BigDecimal balance,

        @Schema(description = "다음 상환 예정일", example = "2026-06-11")
        LocalDate nextRepaymentDate,

        @Schema(description = "이번 달 이자 예정 금액")
        MonthlyInterest monthlyInterest,

        @Schema(description = "원금 잔액 정보")
        Principal principal,

        @Schema(description = "상환 및 납부 내역")
        List<Transaction> transactions
) {

    @Schema(description = "이번 달 이자 예정 금액")
    public record MonthlyInterest(
            @Schema(description = "이자 상환 예정일", example = "2026-06-11")
            LocalDate dueDate,

            @Schema(description = "이자 금액", example = "100000.00")
            BigDecimal amount,

            @Schema(description = "이자 원장 상태", example = "UPCOMING")
            String status
    ) {
    }

    @Schema(description = "원금 잔액 정보")
    public record Principal(
            @Schema(description = "원금 상환 예정일", example = "2026-12-11")
            LocalDate dueDate,

            @Schema(description = "원금 잔액", example = "3000000.00")
            BigDecimal remainingAmount,

            @Schema(description = "원금 상환 상태", example = "UPCOMING")
            String status
    ) {
    }

    @Schema(description = "상환 및 납부 내역")
    public record Transaction(
            @Schema(description = "지갑 거래 publicId", example = "22222222-2222-4222-8222-222222222222")
            UUID transactionPublicId,

            @Schema(description = "거래 유형", example = "INTEREST_PAYMENT")
            String transactionType,

            @Schema(description = "화면 표시용 거래명", example = "4월 이자 상환")
            String title,

            @Schema(description = "표시 금액. 상환성 거래는 음수로 응답", example = "-100000.00")
            BigDecimal amount,

            @Schema(description = "거래 일시", example = "2026-05-11T10:00:00")
            LocalDateTime transactedAt
    ) {
    }
}
