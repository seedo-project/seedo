package dev.seedo.idea;

import dev.seedo.idea.application.ChatSessionAccessDeniedException;
import dev.seedo.idea.application.ChatSessionNotFinalizableException;
import dev.seedo.idea.application.ChatSessionNotFoundException;
import dev.seedo.idea.application.FinalizeChatSessionCommand;
import dev.seedo.idea.application.FinalizeChatSessionResult;
import dev.seedo.idea.application.FinalizeChatSessionService;
import dev.seedo.idea.domain.ChatSessionStatus;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaChatSession;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaChatSessionRepository;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FinalizeChatSessionService} 의 트랜잭션 무결성 + 소유권 가드.
 *
 * <p>네 갱신이 한 트랜잭션에 묶여야 한다 (CLAUDE.md §8.4): ideas INSERT, idea_documents INSERT (v=1),
 * ideas.current_version_id UPDATE, idea_chat_sessions → FINALIZED. 도메인 메서드 + V2 의 CHECK 가
 * status ↔ idea_id ↔ finalized_at 정합성을 강제.
 */
@Transactional
class FinalizeChatSessionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FinalizeChatSessionService service;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository documentRepo;

    @Autowired
    private IdeaChatSessionRepository sessionRepo;

    @Test
    void happy_path_creates_draft_idea_with_v1_document_and_finalizes_session() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));

        FinalizeChatSessionResult result = service.finalize(
                new FinalizeChatSessionCommand(session.getId(), user, "제목", "본문 마크다운"));

        Idea idea = ideaRepo.findById(result.ideaId()).orElseThrow();
        assertThat(idea.getStatus()).isEqualTo(IdeaStatus.DRAFT);
        assertThat(idea.getAuthorId()).isEqualTo(user);
        assertThat(idea.getCurrentVersionId()).isEqualTo(result.documentId());
        assertThat(idea.getPriceCredits()).isEqualTo(10);
        assertThat(idea.getRewardCredits()).isEqualTo(5);

        IdeaDocument doc = documentRepo.findById(result.documentId()).orElseThrow();
        assertThat(doc.getIdeaId()).isEqualTo(idea.getId());
        assertThat(doc.getVersion()).isEqualTo(1);
        assertThat(doc.getTitle()).isEqualTo("제목");
        assertThat(doc.getContentMd()).isEqualTo("본문 마크다운");

        IdeaChatSession reloaded = sessionRepo.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.FINALIZED);
        assertThat(reloaded.getIdeaId()).isEqualTo(idea.getId());
        assertThat(reloaded.getFinalizedAt()).isNotNull();
        assertThat(reloaded.getAbandonedAt()).isNull();
    }

    @Test
    void non_owner_is_denied() {
        UUID owner = UserFixture.create(userRepo);
        UUID intruder = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(owner));

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(session.getId(), intruder, "t", "c")))
                .isInstanceOf(ChatSessionAccessDeniedException.class);

        // 어떤 부수효과도 발생하지 않아야 한다 — 세션은 IN_PROGRESS, idea 0 개.
        assertThat(sessionRepo.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(ChatSessionStatus.IN_PROGRESS);
        assertThat(ideaRepo.count()).isZero();
    }

    @Test
    void already_finalized_session_cannot_be_finalized_again() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));
        service.finalize(new FinalizeChatSessionCommand(session.getId(), user, "t1", "c1"));

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(session.getId(), user, "t2", "c2")))
                .isInstanceOf(ChatSessionNotFinalizableException.class);
    }

    @Test
    void abandoned_session_cannot_be_finalized() {
        UUID user = UserFixture.create(userRepo);
        IdeaChatSession session = sessionRepo.saveAndFlush(new IdeaChatSession(user));
        session.abandon();
        sessionRepo.saveAndFlush(session);

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(session.getId(), user, "t", "c")))
                .isInstanceOf(ChatSessionNotFinalizableException.class);
    }

    @Test
    void missing_session_throws_not_found() {
        UUID user = UserFixture.create(userRepo);

        assertThatThrownBy(() -> service.finalize(
                new FinalizeChatSessionCommand(999_999L, user, "t", "c")))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }
}
