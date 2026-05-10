package dev.seedo.idea;

import dev.seedo.idea.domain.ChatMessageRole;
import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaChatMessage;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.infrastructure.IdeaChatMessageRepository;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdeaChatSession 라이프사이클 (IN_PROGRESS → FINALIZED / ABANDONED) + IdeaChatMessage append.
 * V2 의 양방향 CHECK (status ↔ idea_id / finalized_at / abandoned_at) 가 도메인 메서드와
 * 충돌하지 않는지 확인 — finalize/abandon 메서드가 정확한 컬럼 조합을 만들어야 한다.
 */
@Transactional
class IdeaChatSessionPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaChatSessionRepository sessionRepo;

    @Autowired
    private IdeaChatMessageRepository messageRepo;

    @Test
    void in_progress_to_finalized_round_trip() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.IN_PROGRESS);

        Idea idea = ideaRepo.saveAndFlush(new Idea(user, 10, 5));
        session.finalize(idea.getId());
        sessionRepo.saveAndFlush(session);

        IdeaChatSession reloaded = sessionRepo.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.FINALIZED);
        assertThat(reloaded.getIdeaId()).isEqualTo(idea.getId());
        assertThat(reloaded.getFinalizedAt()).isNotNull();
        assertThat(reloaded.getAbandonedAt()).isNull();
    }

    @Test
    void abandoned_round_trip() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));

        session.abandon();
        sessionRepo.saveAndFlush(session);

        IdeaChatSession reloaded = sessionRepo.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.ABANDONED);
        assertThat(reloaded.getIdeaId()).isNull();
        assertThat(reloaded.getFinalizedAt()).isNull();
        assertThat(reloaded.getAbandonedAt()).isNotNull();
    }

    @Test
    void messages_persist_with_role() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));

        IdeaChatMessage userMsg = messageRepo.saveAndFlush(
                new IdeaChatMessage(session.getId(), ChatMessageRole.USER, "안녕"));
        IdeaChatMessage botMsg = messageRepo.saveAndFlush(
                new IdeaChatMessage(session.getId(), ChatMessageRole.ASSISTANT, "반갑습니다"));

        assertThat(messageRepo.findById(userMsg.getId()).orElseThrow().getRole())
                .isEqualTo(ChatMessageRole.USER);
        assertThat(messageRepo.findById(botMsg.getId()).orElseThrow().getRole())
                .isEqualTo(ChatMessageRole.ASSISTANT);
    }

}
