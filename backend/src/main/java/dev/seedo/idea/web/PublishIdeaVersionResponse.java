package dev.seedo.idea.web;

public record PublishIdeaVersionResponse(
        long ideaId,
        long documentId,
        int version
) {
}
