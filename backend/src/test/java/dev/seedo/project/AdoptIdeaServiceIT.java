package dev.seedo.project;

import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.idea.application.IdeaNotFoundException;
import dev.seedo.idea.application.PurchaseIdeaService;
import dev.seedo.idea.domain.Idea;
import dev.seedo.idea.infrastructure.IdeaDocumentRepository;
import dev.seedo.idea.infrastructure.IdeaRepository;
import dev.seedo.project.application.AdoptCommand;
import dev.seedo.project.application.AdoptIdeaService;
import dev.seedo.project.application.AdoptResult;
import dev.seedo.project.application.AdoptionRequiresPurchaseException;
import dev.seedo.project.application.IdeaNotAdoptableException;
import dev.seedo.project.domain.Project;
import dev.seedo.project.domain.ProjectMember;
import dev.seedo.project.domain.ProjectMemberRole;
import dev.seedo.project.domain.ProjectStatus;
import dev.seedo.project.infrastructure.ProjectMemberRepository;
import dev.seedo.project.infrastructure.ProjectRepository;
import dev.seedo.reward.domain.Reward;
import dev.seedo.reward.domain.RewardStatus;
import dev.seedo.reward.domain.RewardType;
import dev.seedo.reward.infrastructure.RewardRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.IdeaFixture;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AdoptIdeaService} 의 7 단계 트랜잭션 (CLAUDE.md §8.3) 정합성·정책·동시성 검증.
 *
 * <p>클래스 레벨 {@code @Transactional} 미사용 — 동시성 테스트에서 워커 스레드가 셋업 row 를 봐야 함.
 * UUID 무작위라 테스트 간 격리.
 */
class AdoptIdeaServiceIT extends AbstractIntegrationTest {

    private static final int PRICE = 10;
    private static final int REWARD = 5;

    @Autowired
    private AdoptIdeaService service;

    @Autowired
    private PurchaseIdeaService purchaseService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCreditRepository creditRepo;

    @Autowired
    private IdeaRepository ideaRepo;

    @Autowired
    private IdeaDocumentRepository docRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private ProjectMemberRepository memberRepo;

    @Autowired
    private RewardRepository rewardRepo;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Test
    void external_adopter_creates_project_and_pays_reward() {
        Fixture f = setupAdoptableIdeaWithPurchaser();

        AdoptResult result = service.adopt(new AdoptCommand(f.ideaId, f.buyer));

        assertThat(result.rewardPaid()).isTrue();
        assertThat(result.rewardTransactionId()).isNotNull();
        assertThat(result.projectId()).isPositive();

        Project project = projectRepo.findById(result.projectId()).orElseThrow();
        assertThat(project.getIdeaId()).isEqualTo(f.ideaId);
        assertThat(project.getLeaderId()).isEqualTo(f.buyer);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        // 채택 시점 본문이 박제됐는지 — IdeaFixture.createPublished 는 content_md 를 "c" 로 셋업.
        assertThat(project.getIdeaSnapshotMd()).isEqualTo("c");

        List<ProjectMember> members = memberRepo.findAll().stream()
                .filter(m -> m.getProjectId().equals(project.getId())).toList();
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getRole()).isEqualTo(ProjectMemberRole.LEADER);
        assertThat(members.get(0).getUserId()).isEqualTo(f.buyer);
        assertThat(members.get(0).getLeftAt()).isNull();

        // 작성자 잔액 +REWARD
        assertThat(creditRepo.findById(f.author).orElseThrow().getBalance()).isEqualTo(REWARD);

        Reward reward = findAdoptionRewardByIdea(f.ideaId);
        assertThat(reward.getRecipientUserId()).isEqualTo(f.author);
        assertThat(reward.getAmount()).isEqualTo(REWARD);
        assertThat(reward.getStatus()).isEqualTo(RewardStatus.PAID);
        assertThat(reward.getRewardType()).isEqualTo(RewardType.ADOPTION);
        assertThat(reward.getTransactionId()).isEqualTo(result.rewardTransactionId());
        assertThat(reward.getPaidAt()).isNotNull();
    }

    @Test
    void self_adoption_creates_project_without_reward() {
        Fixture f = setupAdoptableIdeaWithPurchaser();

        AdoptResult result = service.adopt(new AdoptCommand(f.ideaId, f.author));

        assertThat(result.rewardPaid()).isFalse();
        assertThat(result.rewardTransactionId()).isNull();

        Project project = projectRepo.findById(result.projectId()).orElseThrow();
        assertThat(project.getLeaderId()).isEqualTo(f.author);

        // 작성자 잔액 변동 없음 — 자가 채택은 보상 skip
        assertThat(creditRepo.findById(f.author).orElseThrow().getBalance()).isZero();
        // rewards row 도 생성되지 않음
        assertThat(rewardRepo.findAll().stream()
                .filter(r -> r.getIdeaId() != null && r.getIdeaId().equals(f.ideaId)))
                .isEmpty();
    }

    @Test
    void draft_idea_is_not_adoptable() {
        UUID author = tx.execute(s -> UserFixture.create(userRepo));
        UUID adopter = tx.execute(s -> UserFixture.create(userRepo));
        Long draftIdeaId = tx.execute(s -> IdeaFixture.createDraft(ideaRepo, author, PRICE, REWARD).getId());

        assertThatThrownBy(() -> service.adopt(new AdoptCommand(draftIdeaId, adopter)))
                .isInstanceOf(IdeaNotAdoptableException.class);

        assertThat(projectRepo.count()).isZero();
    }

    @Test
    void archived_idea_is_not_adoptable() {
        Fixture f = setupAdoptableIdeaWithPurchaser();
        tx.execute(s -> {
            Idea i = ideaRepo.findById(f.ideaId).orElseThrow();
            i.archive();
            return ideaRepo.saveAndFlush(i);
        });

        assertThatThrownBy(() -> service.adopt(new AdoptCommand(f.ideaId, f.buyer)))
                .isInstanceOf(IdeaNotAdoptableException.class);
    }

    @Test
    void external_non_purchaser_is_rejected() {
        UUID author = tx.execute(s -> UserFixture.create(userRepo));
        UUID stranger = tx.execute(s -> UserFixture.create(userRepo));
        Long ideaId = tx.execute(s ->
                IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId());

        assertThatThrownBy(() -> service.adopt(new AdoptCommand(ideaId, stranger)))
                .isInstanceOf(AdoptionRequiresPurchaseException.class);

        assertThat(projectRepo.count()).isZero();
        assertThat(rewardRepo.count()).isZero();
    }

    @Test
    void second_external_adopter_gets_project_but_no_reward() {
        Fixture f = setupAdoptableIdeaWithPurchaser();
        UUID secondBuyer = tx.execute(s -> {
            UUID b = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, b, PRICE);
            purchaseService.purchase(f.ideaId, b);
            return b;
        });

        AdoptResult first = service.adopt(new AdoptCommand(f.ideaId, f.buyer));
        AdoptResult second = service.adopt(new AdoptCommand(f.ideaId, secondBuyer));

        assertThat(first.rewardPaid()).isTrue();
        assertThat(second.rewardPaid()).isFalse();
        assertThat(second.projectId()).isNotEqualTo(first.projectId());

        // 작성자 잔액은 한 번만 += REWARD
        assertThat(creditRepo.findById(f.author).orElseThrow().getBalance()).isEqualTo(REWARD);
        // rewards row 도 1 개만
        long adoptionRewardCount = rewardRepo.findAll().stream()
                .filter(r -> r.getRewardType() == RewardType.ADOPTION
                        && r.getIdeaId() != null && r.getIdeaId().equals(f.ideaId))
                .count();
        assertThat(adoptionRewardCount).isEqualTo(1L);
    }

    @Test
    void missing_idea_throws_not_found() {
        UUID adopter = tx.execute(s -> UserFixture.create(userRepo));

        assertThatThrownBy(() -> service.adopt(new AdoptCommand(999_999L, adopter)))
                .isInstanceOf(IdeaNotFoundException.class);
    }

    /**
     * 같은 아이디어를 두 외부 채택자가 동시에 채택. idea row 락(PESSIMISTIC_WRITE) 이 직렬화하므로
     * 둘 다 프로젝트는 만들어진다 (§12 "여러 프로젝트 허용"). 보상은 정확히 1 회만.
     *
     * <p>ready 배리어로 두 워커가 모두 start.await() 진입한 뒤 동시 해제 — start.countDown() 만 쓰면
     * 한 워커가 늦게 await() 도착해 사실상 순차 실행이 되어 락 회귀를 못 잡는다 (PR #80 패턴 재사용).
     */
    @Test
    void concurrent_adoptions_both_get_projects_but_one_reward() throws Exception {
        UUID author = tx.execute(s -> UserFixture.create(userRepo));
        tx.execute(s -> { UserFixture.grantCredit(creditRepo, author, 0L); return null; });
        Long ideaId = tx.execute(s ->
                IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId());
        UUID b1 = tx.execute(s -> {
            UUID b = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, b, PRICE);
            purchaseService.purchase(ideaId, b);
            return b;
        });
        UUID b2 = tx.execute(s -> {
            UUID b = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, b, PRICE);
            purchaseService.purchase(ideaId, b);
            return b;
        });

        ExecutorService exec = Executors.newFixedThreadPool(2);
        AdoptResult ra;
        AdoptResult rb;
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<AdoptResult> fa = exec.submit(() -> {
                ready.countDown();
                start.await();
                return service.adopt(new AdoptCommand(ideaId, b1));
            });
            Future<AdoptResult> fb = exec.submit(() -> {
                ready.countDown();
                start.await();
                return service.adopt(new AdoptCommand(ideaId, b2));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ra = fa.get(15, TimeUnit.SECONDS);
            rb = fb.get(15, TimeUnit.SECONDS);
        } finally {
            exec.shutdown();
        }

        // 둘 다 프로젝트는 만들어졌다 — 별 ID
        assertThat(ra.projectId()).isNotEqualTo(rb.projectId());

        // 보상은 정확히 1 명만 받았다
        int rewardWinners = (ra.rewardPaid() ? 1 : 0) + (rb.rewardPaid() ? 1 : 0);
        assertThat(rewardWinners).isEqualTo(1);

        // 작성자 잔액 += REWARD (한 번만)
        assertThat(creditRepo.findById(author).orElseThrow().getBalance()).isEqualTo(REWARD);

        // rewards row 도 1 개만 — DB partial UNIQUE 의 마지막 방어선이 제대로 짝이 맞는지 확인
        long adoptionRewardCount = rewardRepo.findAll().stream()
                .filter(r -> r.getRewardType() == RewardType.ADOPTION
                        && r.getIdeaId() != null && r.getIdeaId().equals(ideaId))
                .count();
        assertThat(adoptionRewardCount).isEqualTo(1L);
    }

    private record Fixture(UUID author, UUID buyer, Long ideaId) {
    }

    /**
     * 표준 채택 셋업: author 작성 + buyer 구매 완료한 PUBLISHED 아이디어. author 잔액 0 으로 초기화 (보상 지급 검증용).
     */
    private Fixture setupAdoptableIdeaWithPurchaser() {
        return tx.execute(status -> {
            UUID author = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, author, 0L);
            UUID buyer = UserFixture.create(userRepo);
            UserFixture.grantCredit(creditRepo, buyer, PRICE);
            Long ideaId = IdeaFixture.createPublished(ideaRepo, docRepo, author, PRICE, REWARD).getId();
            purchaseService.purchase(ideaId, buyer);
            return new Fixture(author, buyer, ideaId);
        });
    }

    private Reward findAdoptionRewardByIdea(Long ideaId) {
        return rewardRepo.findAll().stream()
                .filter(r -> r.getRewardType() == RewardType.ADOPTION
                        && r.getIdeaId() != null && r.getIdeaId().equals(ideaId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ADOPTION reward for idea " + ideaId));
    }
}
