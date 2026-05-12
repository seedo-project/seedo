package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdeaChatMessageRepository extends JpaRepository<IdeaChatMessage, Long> {

    /**
     * 세션의 대화 이력을 시간순으로. LLM 컨텍스트 빌드에 사용 — 가장 오래된 메시지가 첫 번째.
     * 같은 시각(created_at) 의 메시지가 있을 경우 id 보조 정렬로 안정성 확보.
     */
    List<IdeaChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);
}
