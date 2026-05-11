package dev.seedo.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 크레딧 적립 결과")
public record AdminGrantResponse(
        @Schema(description = "적립 직후 사용자 잔액(크레딧)", example = "150")
        long balance,
        @Schema(description = "생성된 원장(credit_transactions) 레코드 ID", example = "2048")
        long transactionId
) {
}
