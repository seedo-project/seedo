package dev.seedo.idea.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishIdeaVersionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String contentMd
) {
}
