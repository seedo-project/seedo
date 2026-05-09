package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaChatMessageRepository extends JpaRepository<IdeaChatMessage, Long> {
}
