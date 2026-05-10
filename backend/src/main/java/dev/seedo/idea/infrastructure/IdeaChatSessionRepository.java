package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaChatSessionRepository extends JpaRepository<IdeaChatSession, Long> {
}
