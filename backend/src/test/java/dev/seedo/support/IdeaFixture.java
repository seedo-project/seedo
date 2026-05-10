package dev.seedo.support;

import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;

import java.util.UUID;

/**
 * IT 에서 아이디어 / 문서 / 상태 전이 셋업을 표현하는 정적 헬퍼.
 */
public final class IdeaFixture {

    private IdeaFixture() {
    }

    /** DRAFT 상태 아이디어 1 개 생성. current_version_id 는 NULL. */
    public static Idea createDraft(IdeaRepository ideaRepo, UUID author, int price, int reward) {
        return ideaRepo.saveAndFlush(new Idea(author, price, reward));
    }

    /**
     * PUBLISHED 상태 아이디어 + 첫 버전 문서까지 한 번에. current_version_id 가 그 문서로 박제된 상태.
     * §6.5 산 시점 스냅샷 검증 / §8.2 구매 흐름 셋업에서 표준으로 쓴다.
     */
    public static Idea createPublished(IdeaRepository ideaRepo,
                                       IdeaDocumentRepository docRepo,
                                       UUID author, int price, int reward) {
        Idea idea = ideaRepo.saveAndFlush(new Idea(author, price, reward));
        IdeaDocument doc = docRepo.saveAndFlush(new IdeaDocument(idea.getId(), 1, "t", "c"));
        idea.updateCurrentVersion(doc.getId());
        idea.publish();
        return ideaRepo.saveAndFlush(idea);
    }
}
