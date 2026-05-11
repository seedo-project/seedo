package dev.seedo.project.application;

import java.util.UUID;

public record AdoptCommand(
        long ideaId,
        UUID adopter
) {
}
