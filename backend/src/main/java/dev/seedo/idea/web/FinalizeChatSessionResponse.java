package dev.seedo.idea.web;

public record FinalizeChatSessionResponse(
        long ideaId,
        long documentId,
        int version
) {
}
