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

    /**
     * 세션의 메시지 row 수. finalize 가 LLM 호출과 INSERT 트랜잭션 사이에 새 메시지가 끼어들었는지
     * 비교용 (history 스냅샷 race 가드).
     */
    long countBySessionId(Long sessionId);
}
