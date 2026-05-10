package dev.seedo.idea.web;

public record PurchaseResponse(
        long purchaseId,
        long balance,
        long documentId
) {
}
