package dev.seedo.project.application;

import dev.seedo.credit.application.ChargeCommand;
import dev.seedo.credit.application.ChargeCreditService;
import dev.seedo.credit.application.ChargeResult;
import dev.seedo.credit.domain.CreditType;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.domain.IdeaDocument;
import dev.seedo.idea.domain.IdeaStatus;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaPurchaseRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectMember;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;
import dev.seedo.reward.domain.Reward;
import dev.seedo.reward.domain.RewardType;
import dev.seedo.reward.infrastructure.RewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디어 채택 → 프로젝트 생성 + 작성자 보상 (CLAUDE.md §8.3). 한 트랜잭션에서:
 * <ol>
 *   <li>{@code ideas} SELECT FOR UPDATE — 같은 ideaId 에 대한 동시 채택을 직렬화</li>
 *   <li>PUBLISHED 검증 — DRAFT / ARCHIVED / DELETED 거부</li>
 *   <li>외부 채택자만 구매 이력 검증. 자가 채택자는 skip — §6.6 으로 본인 구매 불가</li>
 *   <li>{@code idea_documents} 현재 버전 본문 → {@code projects.idea_snapshot_md} 박제</li>
 *   <li>{@code projects} INSERT (DRAFT, leader = 채택자)</li>
 *   <li>{@code project_members} INSERT (LEADER, 활성)</li>
 *   <li>(외부 채택자 & 첫 보상) {@link ChargeCreditService#charge} 로 REWARD 원장 + 잔액
 *       → {@code rewards} INSERT (PAID)</li>
 * </ol>
 *
 * <p>"한 아이디어 → 여러 프로젝트, 보상은 첫 채택자만" (§12) 정책:
 * idea row 락 안에서 {@link RewardRepository#existsByIdeaIdAndRewardType} 가 분기를 결정.
 * 락 덕분에 race window 없음. 락 없이도 V4 의 partial UNIQUE
 * {@code rewards_adoption_idea_uniq} 가 마지막 방어선.
 *
 * <p>자가 채택은 step 3, 7 모두 skip — 프로젝트만 생기고 보상은 지급되지 않는다 (§12).
 */
@Service
public class AdoptIdeaService {

    static final String ADOPTION_REFERENCE_TYPE = "ADOPTION";

    private final IdeaRepository ideaRepo;
    private final IdeaDocumentRepository documentRepo;
    private final IdeaPurchaseRepository purchaseRepo;
    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final RewardRepository rewardRepo;
    private final ChargeCreditService chargeService;

    public AdoptIdeaService(IdeaRepository ideaRepo,
                            IdeaDocumentRepository documentRepo,
                            IdeaPurchaseRepository purchaseRepo,
                            ProjectRepository projectRepo,
                            ProjectMemberRepository memberRepo,
                            RewardRepository rewardRepo,
                            ChargeCreditService chargeService) {
        this.ideaRepo = ideaRepo;
        this.documentRepo = documentRepo;
        this.purchaseRepo = purchaseRepo;
        this.projectRepo = projectRepo;
        this.memberRepo = memberRepo;
        this.rewardRepo = rewardRepo;
        this.chargeService = chargeService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdoptResult adopt(AdoptCommand cmd) {
        Idea idea = ideaRepo.findByIdForUpdate(cmd.ideaId())
                .orElseThrow(() -> new IdeaNotFoundException(cmd.ideaId()));

        if (idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new IdeaNotAdoptableException(cmd.ideaId(), idea.getStatus());
        }

        boolean selfAdoption = idea.getAuthorId().equals(cmd.adopter());

        if (!selfAdoption && !purchaseRepo.existsByIdeaIdAndBuyerId(cmd.ideaId(), cmd.adopter())) {
            throw new AdoptionRequiresPurchaseException(cmd.ideaId(), cmd.adopter());
        }

        Long documentId = idea.getCurrentVersionId();
        if (documentId == null) {
            // PUBLISHED 인데 current_version_id 가 NULL — V2 finalize 트랜잭션이 깨졌거나 직접 SQL 조작.
            // 정상 흐름에서 발생 불가능, 데이터 정합성 사고이므로 5xx.
            throw new IllegalStateException(
                    "PUBLISHED idea has no current_version_id: ideaId=" + cmd.ideaId());
        }
        IdeaDocument doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "current_version_id points to missing document: ideaId=" + cmd.ideaId()
                                + ", documentId=" + documentId));

        Project project = projectRepo.saveAndFlush(
                Project.create(cmd.ideaId(), cmd.adopter(), doc.getContentMd()));
        memberRepo.saveAndFlush(ProjectMember.leader(project.getId(), cmd.adopter()));

        if (selfAdoption) {
            return new AdoptResult(project.getId(), false, null);
        }

        // §12: 한 아이디어 → 여러 프로젝트 허용. 보상은 첫 채택자만 — 사전 체크는 idea row 락 안에서
        // 일어나므로 동시 채택자가 둘 다 통과하는 race window 없음. 마지막 방어선은 rewards.idea_id partial UNIQUE.
        if (rewardRepo.existsByIdeaIdAndRewardType(cmd.ideaId(), RewardType.ADOPTION)) {
            return new AdoptResult(project.getId(), false, null);
        }

        int rewardAmount = idea.getRewardCredits();
        if (rewardAmount <= 0) {
            // reward_credits == 0 또는 음수는 정상 흐름에서 불가능 (CHECK >= 0 + 기본값 5).
            // 정책상 채택 보상이 0 인 케이스가 생기면 그 때 분기 추가 — 지금은 사고로 본다.
            throw new IllegalStateException(
                    "idea has non-positive reward_credits: ideaId=" + cmd.ideaId()
                            + ", reward=" + rewardAmount);
        }

        ChargeResult charge = chargeService.charge(new ChargeCommand(
                idea.getAuthorId(),
                rewardAmount,
                CreditType.REWARD,
                ADOPTION_REFERENCE_TYPE,
                String.valueOf(cmd.ideaId()),
                null
        ));

        rewardRepo.saveAndFlush(Reward.adoptionPaid(
                idea.getAuthorId(), rewardAmount, cmd.ideaId(), charge.transactionId()));

        return new AdoptResult(project.getId(), true, charge.transactionId());
    }
}
