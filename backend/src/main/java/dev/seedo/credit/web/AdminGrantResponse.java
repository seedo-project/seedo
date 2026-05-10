package dev.seedo.credit.web;

public record AdminGrantResponse(
        long balance,
        long transactionId
) {
}
