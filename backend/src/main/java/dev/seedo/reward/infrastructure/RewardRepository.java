package dev.seedo.reward.infrastructure;

import dev.seedo.reward.domain.Reward;
import dev.seedo.reward.domain.RewardType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    /**
     * "한 아이디어 → 첫 채택자만 보상" 정책 (CLAUDE.md §12) 의 service-레벨 사전 체크.
     * 마지막 방어선은 V4 의 partial UNIQUE {@code (idea_id) WHERE reward_type='ADOPTION'}.
     */
    boolean existsByIdeaIdAndRewardType(Long ideaId, RewardType rewardType);
}
