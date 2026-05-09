package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdeaPurchaseRepository extends JpaRepository<IdeaPurchase, Long> {

    /** 구매 트랜잭션 진입 직전 중복 차단 (§8.2 step 1). UNIQUE 위반 전에 비즈니스 에러로 변환. */
    boolean existsByIdeaIdAndBuyerId(Long ideaId, UUID buyerId);
}
