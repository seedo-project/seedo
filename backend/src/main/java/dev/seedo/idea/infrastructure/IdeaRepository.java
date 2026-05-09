package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.Idea;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IdeaRepository extends JpaRepository<Idea, Long> {

    /**
     * 구매·상태 전이 트랜잭션의 표준 진입점. SELECT FOR UPDATE 로 행 락을 잡아
     * concurrent 구매·발행을 직렬화한다 (CLAUDE.md §6.2, §8.2).
     * <p>{@code findById} 는 기본 구현이 lock 없이 캐시될 수 있어 별도 메서드 + 명시 JPQL 로 분리.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Idea i where i.id = :id")
    Optional<Idea> findByIdForUpdate(Long id);
}
