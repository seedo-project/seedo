package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaEmbeddingRepository extends JpaRepository<IdeaEmbedding, Long> {
}
