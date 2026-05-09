package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdeaDocumentRepository extends JpaRepository<IdeaDocument, Long> {

    /** 새 버전 발행 시 다음 버전 번호 산출 (§8.4). */
    Optional<IdeaDocument> findFirstByIdeaIdOrderByVersionDesc(Long ideaId);
}
