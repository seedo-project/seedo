package dev.seedo.idea.application;

import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 새 챗봇 세션 생성 (#163). 사용자가 "아이디어가 있으신가요?" 입력창에 첫 메시지를 치기 전 호출 — sessionId
 * 를 받아 이후 {@code POST /chat-sessions/{id}/messages} 와 {@code /finalize} 의 진입점으로 사용.
 *
 * <p>단순 INSERT 만 — IN_PROGRESS 로 시작. 같은 사용자의 동시 세션 허용 (V2 스키마에 unique 제약 없음 —
 * 사용자가 여러 아이디어를 병렬로 brainstorm 할 수 있게).
 *
 * <p>락 / 멱등성 키 불필요 — 빈 세션은 finalize 진입에서 거부되어 (history 0 → {@link EmptyChatHistoryException})
 * 사용자가 잘못 만든 세션이 idea 까지 가지 않는다.
 */
@Service
public class StartChatSessionService {

    private final IdeaChatSessionRepository sessionRepo;

    public StartChatSessionService(IdeaChatSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StartChatSessionResult start(UUID userId) {
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(userId));
        return new StartChatSessionResult(session.getId(), session.getCreatedAt());
    }
}
